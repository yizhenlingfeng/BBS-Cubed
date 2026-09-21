package net.irisshaders.iris.compat.sodium.impl.vertex_format.terrain_xhfp;

import me.jellysquid.mods.sodium.client.render.chunk.terrain.material.Material;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.irisshaders.iris.compat.sodium.impl.block_context.BlockContextHolder;
import net.irisshaders.iris.compat.sodium.impl.block_context.ContextAwareVertexWriter;
import net.irisshaders.iris.vertices.ExtendedDataHelper;
import net.irisshaders.iris.vertices.NormI8;
import net.irisshaders.iris.vertices.NormalHelper;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import static net.irisshaders.iris.compat.sodium.impl.vertex_format.terrain_xhfp.XHFPModelVertexType.STRIDE;
import static net.irisshaders.iris.compat.sodium.impl.vertex_format.terrain_xhfp.XHFPModelVertexType.encodeLight;
import static net.irisshaders.iris.compat.sodium.impl.vertex_format.terrain_xhfp.XHFPModelVertexType.encodeTexture;
import static net.irisshaders.iris.compat.sodium.impl.vertex_format.terrain_xhfp.XHFPModelVertexType.packLightAndData;
import static net.irisshaders.iris.compat.sodium.impl.vertex_format.terrain_xhfp.XHFPModelVertexType.packPositionHi;
import static net.irisshaders.iris.compat.sodium.impl.vertex_format.terrain_xhfp.XHFPModelVertexType.packPositionLo;
import static net.irisshaders.iris.compat.sodium.impl.vertex_format.terrain_xhfp.XHFPModelVertexType.packTexture;
import static net.irisshaders.iris.compat.sodium.impl.vertex_format.terrain_xhfp.XHFPModelVertexType.quantizePosition;

public class XHFPTerrainVertex implements ChunkVertexEncoder, ContextAwareVertexWriter {
	private final QuadViewTerrain quad = new QuadViewTerrain();
	private final Vector3f normal = new Vector3f();

	private BlockContextHolder contextHolder;

	private boolean flipUpcomingNormal;

	// TODO: FIX

	/*@Override
	public void copyQuadAndFlipNormal() {
		ensureCapacity(4);

		MemoryUtil.memCopy(this.writePointer - STRIDE * 4, this.writePointer, STRIDE * 4);

		// Now flip vertex normals
		int packedNormal = MemoryUtil.memGetInt(this.writePointer + 32);
		int inverted = NormalHelper.invertPackedNormal(packedNormal);

		MemoryUtil.memPutInt(this.writePointer + 32, inverted);
		MemoryUtil.memPutInt(this.writePointer + 32 + STRIDE, inverted);
		MemoryUtil.memPutInt(this.writePointer + 32 + STRIDE * 2, inverted);
		MemoryUtil.memPutInt(this.writePointer + 32 + STRIDE * 3, inverted);

		// We just wrote 4 vertices, advance by 4
		for (int i = 0; i < 4; i++) {
			this.advance();
		}

		// Ensure vertices are flushed
		this.flush();
	}*/

	@Override
	public void iris$setContextHolder(BlockContextHolder holder) {
		this.contextHolder = holder;
	}

	@Override
	public void flipUpcomingQuadNormal() {
		flipUpcomingNormal = true;
	}

	@Override
	public long write(long ptr, Material material, Vertex[] vertices, int section) {
		quad.set(vertices);

		// Calculate the center point of the texture region which is mapped to the quad
		float texCentroidU = 0.0f;
		float texCentroidV = 0.0f;

		for (var vertex : vertices) {
			texCentroidU += vertex.u;
			texCentroidV += vertex.v;
		}

		texCentroidU *= (1.0f / 4.0f);
		texCentroidV *= (1.0f / 4.0f);

		int midUV = XHFPModelVertexType.encodeTextureOld(texCentroidU, texCentroidV);

		if (flipUpcomingNormal) {
			NormalHelper.computeFaceNormalFlipped(normal, quad);
			flipUpcomingNormal = false;
		} else {
			NormalHelper.computeFaceNormal(normal, quad);
		}

		int normalV = NormI8.pack(normal);

		int tangent = NormalHelper.computeTangent(normal.x, normal.y, normal.z, quad);


		for (int i = 0; i < 4; i++) {
			var vertex = vertices[i];

			int x = quantizePosition(vertex.x);
			int y = quantizePosition(vertex.y);
			int z = quantizePosition(vertex.z);

			int u = encodeTexture(texCentroidU, vertex.u);
			int v = encodeTexture(texCentroidV, vertex.v);

			int light = encodeLight(vertex.light);

			MemoryUtil.memPutInt(ptr +  0L, packPositionHi(x, y, z));
			MemoryUtil.memPutInt(ptr +  4L, packPositionLo(x, y, z));
			MemoryUtil.memPutInt(ptr +  8L, vertex.color);
			MemoryUtil.memPutInt(ptr + 12L, packTexture(u, v));
			MemoryUtil.memPutInt(ptr + 16L, packLightAndData(light, material.bits(), section));
			MemoryUtil.memPutInt(ptr + 20L, midUV);
			MemoryUtil.memPutInt(ptr + 24L, tangent);
			MemoryUtil.memPutInt(ptr + 28L, normalV);
			MemoryUtil.memPutShort(ptr + 32L, contextHolder.blockId);
			MemoryUtil.memPutShort(ptr + 34, contextHolder.renderType);
			MemoryUtil.memPutInt(ptr + 36, contextHolder.ignoreMidBlock ? 0 : ExtendedDataHelper.computeMidBlock(vertex.x, vertex.y, vertex.z, contextHolder.localPosX, contextHolder.localPosY, contextHolder.localPosZ));
			MemoryUtil.memPutByte(ptr + 39, contextHolder.lightValue);

			ptr += STRIDE;
		}

		return ptr;
	}
}
