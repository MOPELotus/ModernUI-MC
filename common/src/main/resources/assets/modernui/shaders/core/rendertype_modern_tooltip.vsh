#version 330
#extension GL_ARB_separate_shader_objects : require
// This file is part of Modern UI.
// Copyright (C) 2024 BloCamLimb.
// Licensed under LGPL-3.0-or-later.

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(std140) uniform ModernTooltip {
    mat4 u_LocalMat;
    vec4 u_PushData0;
    vec3 u_PushData1;
    vec4 u_PushData2;
    vec4 u_PushData3;
    vec4 u_PushData4;
    vec4 u_PushData5;
};

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;

layout(location = 0) out vec2 f_Position;

void main() {
    f_Position = Position.xy;
    // localMat is 2D affine, z/w is ignored
    vec4 localPos = u_LocalMat * vec4(Position, 1.0);

    gl_Position = ProjMat * ModelViewMat * vec4(localPos.xy, Position.z, 1.0);
}
