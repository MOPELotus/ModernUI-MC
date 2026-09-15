// This file is part of Modern UI.
// Copyright (C) 2024 BloCamLimb.
// Licensed under LGPL-3.0-or-later.
#version 400
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec2 texCoord0;

layout(location = 0) out vec4 fragColor;

void main() {
    float lod = textureQueryLod(Sampler0, texCoord0).y;
    vec4 texColor;
    if (lod <= 1.0) {
        // must be BILINEAR sampling
        texColor = textureLod(Sampler0, texCoord0, 0.0);

        // apply distance field
        float dist = texColor.a - 127./255. + 0.04;

        /*vec2 grad = vec2(dFdx(dist), dFdy(dist));
        float afwidth = 0.7 * length(grad);*/ // L2 norm (exact)

        // Minecraft uses non-premultiplied alpha blending
        texColor.a = clamp(dist / fwidth(dist) + 0.5, 0.0, 1.0);
    } else {
        texColor = textureLod(Sampler0, texCoord0, lod);
    }

    vec4 color = texColor * vertexColor * ColorModulator;
    if (color.a < 0.01) discard; // requires alpha test
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
