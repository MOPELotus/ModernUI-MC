// Matches Minecraft 26.3 DynamicGpuData.Transform.write():
// model view, texture matrix (border colors), color modulator, model offset.
// writeTransform's argument order is different from its buffer layout.
layout(std140) uniform ModernTooltip {
    mat4 u_LocalMat;
    vec4 u_PushData2;
    vec4 u_PushData3;
    vec4 u_PushData4;
    vec4 u_PushData5;
    vec4 u_PushData0;
    vec3 u_PushData1;
};
