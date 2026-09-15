#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 col = texture(InSampler, texCoord);
    fragColor = vec4(vec3(dot(col.rgb,vec3(0.2126,0.7152,0.0722))), col.a);
}