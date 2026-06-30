/*
 * Modern UI.
 * Copyright (C) 2025 BloCamLimb. All rights reserved.
 *
 * Modern UI is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Modern UI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Modern UI. If not, see <https://www.gnu.org/licenses/>.
 */

package icyllis.modernui.mc.b3d;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.opengl.FrameBufferCache;
import com.mojang.blaze3d.opengl.GlConst;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import icyllis.arc3d.core.SharedPtr;
import icyllis.arc3d.engine.Engine;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

// Wrap Arc3D GL texture in Blaze3D GL backend as a unique owner
public class GlTexture_Wrapped extends GlTexture {

    private static final Field BACKEND_FIELD;

    static {
        try {
            BACKEND_FIELD = GpuDevice.class.getDeclaredField("backend");
            BACKEND_FIELD.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // usage ref count is managed by caller
    // we don't care about command buffer usage in GL backend
    public icyllis.arc3d.opengl.GLTexture source;

    public GlTexture_Wrapped(@Nonnull @SharedPtr icyllis.arc3d.opengl.GLTexture source) {
        super(USAGE_COPY_SRC | USAGE_TEXTURE_BINDING |
                        (source.isRenderable() ? USAGE_RENDER_ATTACHMENT : 0),
                source.getLabel(),
                source.getGLFormat() == GlConst.GL_RGBA8 ? GpuFormat.RGBA8_UNORM : GpuFormat.R8_UNORM,
                source.getWidth(), source.getHeight(),
                /*depthOrLayers*/ 1, source.getMipLevelCount(),
                source.getHandle(), frameBufferCache());
        assert source.getImageType() == Engine.ImageType.k2D;
        assert source.getGLFormat() == GlConst.GL_RGBA8 || source.getGLFormat() == GlConst.GL_R8;
        assert source.getDepth() == 1;
        assert source.getArraySize() == 1;
        assert source.getSampleCount() == 1;
        this.source = source; // move
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            source.unref();
        }
    }

    // we can increment usage cnt if not reached to zero,
    // to use the same wrapper object.
    // otherwise cause an assertion error
    public void touch() {
        if (closed) {
            closed = false;
            source.ref();
        }
    }

    @Override
    public void addViews() {
    }

    @Override
    public void removeViews() {
    }

    private static FrameBufferCache frameBufferCache() {
        try {
            Object backend = BACKEND_FIELD.get(RenderSystem.getDevice());
            Method method = backend.getClass().getDeclaredMethod("frameBufferCache");
            method.setAccessible(true);
            return (FrameBufferCache) method.invoke(backend);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to access Minecraft OpenGL framebuffer cache", e);
        }
    }
}
