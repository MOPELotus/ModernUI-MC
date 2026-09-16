#version 330
#extension GL_ARB_separate_shader_objects : require
// This file is part of Modern UI.
// Copyright (C) 2024 BloCamLimb.
// Licensed under LGPL-3.0-or-later.

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

#include <modernui:tooltip_uniforms.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;

layout(location = 0) out vec2 f_Position;

void main() {
    f_Position = Position.xy;
    // localMat is 2D affine, z/w is ignored
    vec4 localPos = u_LocalMat * vec4(Position, 1.0);

    gl_Position = ProjMat * ModelViewMat * vec4(localPos.xy, Position.z, 1.0);
}
