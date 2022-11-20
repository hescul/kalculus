package com.flyng.kalculus.renderable

import com.flyng.kalculus.core.manager.MaterialManager
import com.flyng.kalculus.core.manager.ThemeManager
import com.flyng.kalculus.renderable.mesh.Mesh
import com.flyng.kalculus.visual.Visual
import com.flyng.kalculus.visual.primitive.Topology
import com.flyng.kalculus.visual.vertex.ByteSize
import com.flyng.kalculus.visual.vertex.ColorAttribute
import com.flyng.kalculus.visual.vertex.PositionAttribute
import com.google.android.filament.*
import com.google.android.filament.RenderableManager.PrimitiveType
import java.nio.ByteBuffer
import java.nio.ByteOrder

actual fun Visual.bridge() = object : Renderable {

    override fun loadMesh(engine: Engine, materialManager: MaterialManager, themeManager: ThemeManager): Mesh {
        // create and load vertex buffer
        val vertexBuffer = loadVertexBuffer(engine, themeManager)

        // create and load index buffer
        val indexBuffer = loadIndexBuffer(engine)

        return createMesh(engine, vertexBuffer, indexBuffer, materialManager, themeManager)
    }

    private fun loadVertexBuffer(engine: Engine, themeManager: ThemeManager): VertexBuffer {
        val color = themeManager.renderableColor().value
        val vertices = vertices(color).also {
            if (it.isEmpty()) throw RuntimeException("A Visual must have at least 1 vertex.")
        }
        val vertexCount = vertices.size
        val attributes = vertices[0].data
        val vertexSize = attributes.fold(0) { prev, attr -> prev + attr.size }

        val vertexData = ByteBuffer.allocate(vertexCount * vertexSize)
            // respect the native byte order
            .order(ByteOrder.nativeOrder())
            // put vertex data
            .apply {
                vertices.forEach { vertex ->
                    vertex.data.forEach {
                        when (it) {
                            is PositionAttribute -> {
                                putFloat(it.x)
                                putFloat(it.y)
                                putFloat(it.z)
                            }

                            is ColorAttribute -> {
                                putInt(it.color)
                            }
                        }
                    }
                }
            }
            // make sure the cursor is pointing in the right place in the byte buffer
            .flip()

        // declare layout of the mesh
        val vertexBuffer = VertexBuffer.Builder()
            .bufferCount(1)
            .vertexCount(vertexCount)
            .apply {
                attributes.forEachIndexed { idx, attr ->
                    // Because we interleave position and color data we must specify offset and stride
                    // We could use de-interleaved data by declaring two buffers and giving each
                    // attribute a different buffer index
                    when (attr) {
                        is PositionAttribute -> {
                            attribute(
                                VertexBuffer.VertexAttribute.POSITION,
                                0,
                                VertexBuffer.AttributeType.FLOAT3,
                                attributes
                                    .dropLast(attributes.size - idx)
                                    .fold(0) { prev, it -> prev + it.size },
                                vertexSize
                            )
                            if (!attr.normalized) {
                                normalized(VertexBuffer.VertexAttribute.POSITION)
                            }
                        }

                        is ColorAttribute -> {
                            attribute(
                                VertexBuffer.VertexAttribute.COLOR,
                                0,
                                VertexBuffer.AttributeType.UBYTE4,
                                attributes
                                    .dropLast(attributes.size - idx)
                                    .fold(0) { prev, it -> prev + it.size },
                                vertexSize
                            )
                            if (!attr.normalized) {
                                // We store colors as unsigned bytes but since we want values between 0 and 1
                                // in the material (shaders), we must mark the attribute as normalized
                                normalized(VertexBuffer.VertexAttribute.COLOR)
                            }
                        }
                    }
                }
            }
            .build(engine)

        // Feed the vertex data to the mesh
        // We only set 1 buffer because the data is interleaved
        return vertexBuffer.apply { setBufferAt(engine, 0, vertexData) }
    }

    private fun loadIndexBuffer(engine: Engine): IndexBuffer {
        val indices = indices().also {
            if (it.isEmpty()) throw RuntimeException("A Visual must have at least 1 index.")
        }
        val indexCount = indices.size

        // create indices
        val indexData = ByteBuffer.allocate(indexCount * ByteSize.SHORT)
            .order(ByteOrder.nativeOrder())
            .apply {
                indices.forEach {
                    putShort(it)
                }
            }
            .flip()

        val indexBuffer = IndexBuffer.Builder()
            .indexCount(indexCount)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(engine)

        return indexBuffer.apply { setBuffer(engine, indexData) }
    }

    private fun createMesh(
        engine: Engine,
        vertexBuffer:
        VertexBuffer,
        indexBuffer: IndexBuffer,
        materialManager: MaterialManager,
        themeManager: ThemeManager,
    ): Mesh {
        val primitives = primitives().also {
            if (it.isEmpty()) throw RuntimeException("A Visual must have at least 1 primitive.")
        }
        val primitiveCount = primitives.size

        val boundary = boundary()

        // To create a renderable we first create a generic entity
        val entity = EntityManager.get().create()

        // Overall bounding box of the renderable
        val box = with(boundary) {
            Box(centerX, centerY, centerZ, halfExtentX, halfExtentY, halfExtentZ)
        }

        // Store all materials of the renderable
        val materials = mutableListOf<MaterialInstance>()

        // We then create a renderable component on that entity
        // A renderable is made of several primitives
        RenderableManager.Builder(primitiveCount)
            .boundingBox(box)
            .apply {
                primitives.forEachIndexed { index, (topology, offset, count, material) ->
                    geometry(
                        index,
                        when (topology) {
                            Topology.POINTS -> PrimitiveType.POINTS
                            Topology.LINES -> PrimitiveType.LINES
                            Topology.TRIANGLES -> PrimitiveType.TRIANGLES
                        },
                        vertexBuffer,
                        indexBuffer,
                        offset,
                        count
                    )
                    material(
                        index,
                        materialManager[material].let { filamat ->
                            if (filamat.hasParameter("baseColor")) {
                                filamat.createInstance().also { instance ->
                                    val color = themeManager.renderableColor()
                                    instance.setParameter(
                                        "baseColor", Colors.RgbType.SRGB, color.red, color.green, color.blue
                                    )
                                    materials.add(instance)
                                }
                            } else {
                                filamat.defaultInstance
                            }
                        }
                    )
                }
            }
            .build(engine, entity)

        return Mesh(entity, indexBuffer, vertexBuffer, box, materials)
    }
}
