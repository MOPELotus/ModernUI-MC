#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 blur = vec4(0.0);

    vec2 uv = texCoord;
    uv -= 0.5;
    for (float r = 0; r < 10.0; r += 1.0) {
        blur += texture(InSampler, uv * (0.9 + 0.011 * r) + 0.5);
    }

    fragColor = vec4(blur.rgb / 10.0, 1.0);
}