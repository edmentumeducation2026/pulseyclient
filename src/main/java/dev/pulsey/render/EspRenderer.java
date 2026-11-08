package dev.pulsey.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.pulsey.module.Module;
import dev.pulsey.module.ModuleManager;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Batches translucent filled boxes (used for both fills and outlines, which are drawn as thin boxes
 * so line thickness is fully configurable) and draws them once per frame.
 * Written against the Fabric docs' 1.21.11 "custom render pipeline" example (Mojang mappings).
 */
public final class EspRenderer {
    private static final Batch DEPTH = new Batch(RenderPipelines.DEBUG_FILLED_BOX, "esp");

    /** Camera position this frame; modules use it for distance maths. */
    public static double camX, camY, camZ;
    private static Matrix4fc pose;

    private EspRenderer() {}

    // ---- frame entry point (WorldRenderEvents.BEFORE_TRANSLUCENT) ----
    public static void render(WorldRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Vec3 cam = ctx.worldState().cameraRenderState.pos;
        camX = cam.x; camY = cam.y; camZ = cam.z;
        pose = ctx.matrices().last().pose();

        for (Module m : ModuleManager.all()) if (m.enabled) m.onWorldRender();

        DEPTH.flush(mc);
    }

    public static void close() {
        DEPTH.close();
    }

    // ---- public drawing API (coordinates are world space; camera offset is applied in doubles) ----
    public static void fillBox(AABB bb, int rgb, float alpha) {
        if (alpha <= 0.004f) return;
        BufferBuilder b = DEPTH.buf();
        addBox(b,
                bb.minX - camX, bb.minY - camY, bb.minZ - camZ,
                bb.maxX - camX, bb.maxY - camY, bb.maxZ - camZ,
                rgb, alpha);
    }

    public static void edgeBox(AABB bb, int rgb, float alpha, float thickness,
                               boolean corners, float cornerFraction) {
        if (alpha <= 0.004f) return;
        BufferBuilder b = DEPTH.buf();
        double h = thickness / 2.0;
        double[] mn = {bb.minX - camX, bb.minY - camY, bb.minZ - camZ};
        double[] mx = {bb.maxX - camX, bb.maxY - camY, bb.maxZ - camZ};
        double[] lo = new double[3], hi = new double[3];

        for (int ax = 0; ax < 3; ax++) {
            int o1 = (ax + 1) % 3, o2 = (ax + 2) % 3;
            double len = mx[ax] - mn[ax];
            for (int i = 0; i < 2; i++) {
                for (int j = 0; j < 2; j++) {
                    double c1 = i == 0 ? mn[o1] : mx[o1];
                    double c2 = j == 0 ? mn[o2] : mx[o2];
                    lo[o1] = c1 - h; hi[o1] = c1 + h;
                    lo[o2] = c2 - h; hi[o2] = c2 + h;
                    if (corners) {
                        double seg = len * cornerFraction;
                        lo[ax] = mn[ax] - h; hi[ax] = mn[ax] + seg;
                        addBox(b, lo[0], lo[1], lo[2], hi[0], hi[1], hi[2], rgb, alpha);
                        lo[ax] = mx[ax] - seg; hi[ax] = mx[ax] + h;
                        addBox(b, lo[0], lo[1], lo[2], hi[0], hi[1], hi[2], rgb, alpha);
                    } else {
                        lo[ax] = mn[ax] - h; hi[ax] = mx[ax] + h;
                        addBox(b, lo[0], lo[1], lo[2], hi[0], hi[1], hi[2], rgb, alpha);
                    }
                }
            }
        }
    }

    private static void addBox(BufferBuilder b, double x0, double y0, double z0,
                               double x1, double y1, double z1, int rgb, float a) {
        float minX = (float) x0, minY = (float) y0, minZ = (float) z0;
        float maxX = (float) x1, maxY = (float) y1, maxZ = (float) z1;
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, bl = (rgb & 255) / 255f;
        Matrix4fc m = pose;

        // Front
        b.addVertex(m, minX, minY, maxZ).setColor(r, g, bl, a);
        b.addVertex(m, maxX, minY, maxZ).setColor(r, g, bl, a);
        b.addVertex(m, maxX, maxY, maxZ).setColor(r, g, bl, a);
        b.addVertex(m, minX, maxY, maxZ).setColor(r, g, bl, a);
        // Back
        b.addVertex(m, maxX, minY, minZ).setColor(r, g, bl, a);
        b.addVertex(m, minX, minY, minZ).setColor(r, g, bl, a);
        b.addVertex(m, minX, maxY, minZ).setColor(r, g, bl, a);
        b.addVertex(m, maxX, maxY, minZ).setColor(r, g, bl, a);
        // Left
        b.addVertex(m, minX, minY, minZ).setColor(r, g, bl, a);
        b.addVertex(m, minX, minY, maxZ).setColor(r, g, bl, a);
        b.addVertex(m, minX, maxY, maxZ).setColor(r, g, bl, a);
        b.addVertex(m, minX, maxY, minZ).setColor(r, g, bl, a);
        // Right
        b.addVertex(m, maxX, minY, maxZ).setColor(r, g, bl, a);
        b.addVertex(m, maxX, minY, minZ).setColor(r, g, bl, a);
        b.addVertex(m, maxX, maxY, minZ).setColor(r, g, bl, a);
        b.addVertex(m, maxX, maxY, maxZ).setColor(r, g, bl, a);
        // Top
        b.addVertex(m, minX, maxY, maxZ).setColor(r, g, bl, a);
        b.addVertex(m, maxX, maxY, maxZ).setColor(r, g, bl, a);
        b.addVertex(m, maxX, maxY, minZ).setColor(r, g, bl, a);
        b.addVertex(m, minX, maxY, minZ).setColor(r, g, bl, a);
        // Bottom
        b.addVertex(m, minX, minY, minZ).setColor(r, g, bl, a);
        b.addVertex(m, maxX, minY, minZ).setColor(r, g, bl, a);
        b.addVertex(m, maxX, minY, maxZ).setColor(r, g, bl, a);
        b.addVertex(m, minX, minY, maxZ).setColor(r, g, bl, a);
    }

    // ---- one buffer + pipeline pair ----
    private static final class Batch {
        private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
        private static final Vector3f MODEL_OFFSET = new Vector3f();
        private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

        private final RenderPipeline pipeline;
        private final String label;
        private final ByteBufferBuilder allocator = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
        private BufferBuilder buffer;
        private MappableRingBuffer vertexBuffer;

        Batch(RenderPipeline pipeline, String label) {
            this.pipeline = pipeline;
            this.label = label;
        }

        BufferBuilder buf() {
            if (buffer == null) {
                buffer = new BufferBuilder(allocator, pipeline.getVertexFormatMode(), pipeline.getVertexFormat());
            }
            return buffer;
        }

        void flush(Minecraft mc) {
            if (buffer == null) return; // nothing was added this frame
            MeshData mesh = buffer.buildOrThrow();
            buffer = null;
            MeshData.DrawState ds = mesh.drawState();
            VertexFormat format = ds.format();

            GpuBuffer vertices = upload(ds, format, mesh);
            draw(mc, mesh, ds, vertices, format);
            vertexBuffer.rotate();
        }

        private GpuBuffer upload(MeshData.DrawState ds, VertexFormat format, MeshData mesh) {
            int size = ds.vertexCount() * format.getVertexSize();
            if (vertexBuffer == null || vertexBuffer.size() < size) {
                if (vertexBuffer != null) vertexBuffer.close();
                vertexBuffer = new MappableRingBuffer(() -> "pulsey esp " + label,
                        GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, size);
            }
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            try (GpuBuffer.MappedView view = encoder.mapBuffer(
                    vertexBuffer.currentBuffer().slice(0, mesh.vertexBuffer().remaining()), false, true)) {
                MemoryUtil.memCopy(mesh.vertexBuffer(), view.data());
            }
            return vertexBuffer.currentBuffer();
        }

        private void draw(Minecraft mc, MeshData mesh, MeshData.DrawState ds, GpuBuffer vertices, VertexFormat format) {
            GpuBuffer indices;
            VertexFormat.IndexType indexType;

            if (pipeline.getVertexFormatMode() == VertexFormat.Mode.QUADS) {
                mesh.sortQuads(allocator, RenderSystem.getProjectionType().vertexSorting());
                indices = pipeline.getVertexFormat().uploadImmediateIndexBuffer(mesh.indexBuffer());
                indexType = mesh.drawState().indexType();
            } else {
                RenderSystem.AutoStorageIndexBuffer seq = RenderSystem.getSequentialBuffer(pipeline.getVertexFormatMode());
                indices = seq.getBuffer(ds.indexCount());
                indexType = seq.type();
            }

            GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
                    .writeTransform(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "pulsey esp draw " + label,
                    mc.getMainRenderTarget().getColorTextureView(), OptionalInt.empty(),
                    mc.getMainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
                pass.setPipeline(pipeline);
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", transforms);
                pass.setVertexBuffer(0, vertices);
                pass.setIndexBuffer(indices, indexType);
                pass.drawIndexed(0, 0, ds.indexCount(), 1);
            }
            mesh.close();
        }

        void close() {
            allocator.close();
            if (vertexBuffer != null) {
                vertexBuffer.close();
                vertexBuffer = null;
            }
        }
    }
}
