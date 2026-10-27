#!/usr/bin/env python3
"""Validate every published loader/edition pair using the final, shaded JARs."""
import io
import json
from pathlib import Path
import re
import sys
import tomllib
import zipfile

root = Path(__file__).resolve().parents[1]
properties = dict(line.split('=', 1) for line in (root / 'gradle.properties').read_text().splitlines()
                  if '=' in line and not line.lstrip().startswith('#'))
mc, version = properties['minecraft_version'], properties['mod_version']
output, *loaders = sys.argv[1:]
assert loaders and len(loaders) == len(set(loaders)), 'specify unique loader names'
assert re.fullmatch(r'\d+\.\d+\.\d+\.\d+', version), f'unexpected release version: {version}'
expected = set()
for loader in loaders:
    label = {'fabric': 'Fabric', 'forge': 'Forge', 'neoforge': 'NeoForge'}[loader]
    for misans in (False, True):
        variant = 'misans' if misans else 'standard'
        suffix = '-misans' if misans else ''
        name = f'ModernUI-{label}-{mc}-{version}-universal{suffix}.jar'
        expected.add(name)
        with zipfile.ZipFile(Path(output) / name) as jar:
            entries = set(jar.namelist())
            assert jar.testzip() is None, name
            manifest = jar.read('META-INF/MANIFEST.MF').decode().replace('\r\n ', '')
            assert re.search(rf'^ModernUI-Font-Variant: {variant}\r?$', manifest, re.M), name
            if loader == 'fabric':
                meta = json.loads(jar.read('fabric.mod.json'))
                ident, actual_version, display = meta['id'], meta['version'], meta['name']
            else:
                path = 'META-INF/neoforge.mods.toml' if loader == 'neoforge' else 'META-INF/mods.toml'
                meta = tomllib.loads(jar.read(path).decode())['mods'][0]
                ident, actual_version, display = meta['modId'], meta['version'], meta['displayName']
            assert (ident, actual_version, display) == ('modernui', version,
                    'Modern UI (MiSans)' if misans else 'Modern UI'), name
            for cls in ('MiSansInstaller', 'MiSansSetup'):
                assert (f'icyllis/modernui/mc/{cls}.class' in entries) == misans, (name, cls)
            if not misans:
                for cls in ('ModernUIClient', 'mixin/MixinMinecraft'):
                    data = jar.read(f'icyllis/modernui/mc/{cls}.class')
                    assert b'icyllis/modernui/mc/MiSansSetup' not in data, (name, cls)
            for cls in ('text/TextRunRenderState', 'text/TextSampling', 'FontVariantConfig'):
                assert f'icyllis/modernui/mc/{cls}.class' in entries, (name, cls)
            assert jar.read('icyllis/modernui/mc/FontVariant.class').find(
                    f'client-{variant}.toml'.encode()) >= 0, name
            mixins = json.loads(jar.read(f'mixins.modernui-{loader}.json'))
            for mixin in ('MixinSPIRVCodeGenerator', 'MixinVulkanDeviceManager',
                          'MixinVulkanImage', 'MixinVulkanRenderer',
                          'MixinGpuBufferPoolAddress', 'MixinParcelBufferFree',
                          'MixinVulkanBindingBufferFree', 'MixinVulkanLongBufferFree',
                          'MixinWindowsWindowBorderAbi', 'AccessVulkanPipelines',
                          'MixinVulkanPipelineLifecycle', 'MixinVulkanGpuDeviceOwnership',
                          'MixinVulkanGpuResourceOwnership', 'MixinVulkanCloudOwnership',
                          'MixinVulkanImageUpload', 'AccessVulkanGlFramebuffer',
                          'MixinVulkanCommandEncoderOwnership'):
                assert mixin in mixins['client'], (name, 'unregistered compatibility Mixin', mixin)
                assert f'icyllis/modernui/mc/mixin/{mixin}.class' in entries, (name, mixin)
            if loader == 'neoforge':
                for mixin in ('MixinVulkanCommandEncoder', 'MixinVulkanUploadManager',
                              'MixinNeoVulkanShutdown', 'MixinNeoVulkanShutdownGate',
                              'MixinVulkanTextureViewOwnership', 'MixinVulkanTextureViewLifecycle'):
                    assert mixin in mixins['client'], (name, 'unregistered Neo compatibility Mixin', mixin)
                    assert f'icyllis/modernui/mc/mixin/{mixin}.class' in entries, (name, mixin)
                assert 'icyllis/modernui/mc/neoforge/NeoVulkanShutdown.class' in entries, name
                # These Mixin targets must load through FML's transforming game
                # classloader. Plain nested compiler JARs become plugin libraries.
                for cls in ('compiler/spirv/SPIRVCodeGenerator', 'granite/GraniteDevice'):
                    assert f'icyllis/arc3d/{cls}.class' in entries, (name, cls)
                for resource in ('cm_common.aksl', 'cm_glsl_frag.aksl', 'cm_glsl_vert.aksl'):
                    assert f'icyllis/arc3d/shaders/{resource}' in entries, (name, resource)
                assert not any(path == 'module-info.class' or
                               re.fullmatch(r'META-INF/versions/[^/]+/module-info.class', path)
                               for path in entries), (name, 'shaded module descriptor')
                metadata = json.loads(jar.read('META-INF/jarjar/metadata.json'))
                assert not any(item['identifier'] == {
                    'group': 'dev.icyllis', 'artifact': 'arc3d-compiler'}
                    for item in metadata['jars']), (name, 'compiler still registered as JarJar library')
                for path in entries:
                    if path.endswith('.jar'):
                        with zipfile.ZipFile(io.BytesIO(jar.read(path))) as nested:
                            assert 'icyllis/arc3d/compiler/spirv/SPIRVCodeGenerator.class' not in nested.namelist(), (
                                name, path, 'duplicate nested compiler target')
            if loader == 'fabric':
                assert 'MixinVulkanDrawBuffersLifecycle' in mixins['client'], name
                assert 'icyllis/modernui/mc/mixin/MixinVulkanDrawBuffersLifecycle.class' in entries, name
        print(f'PASS: {name}')
actual = {p.name for p in Path(output).glob('*.jar')}
assert actual == expected, f'missing={expected-actual}, unexpected={actual-expected}'
print(f'PASS: {len(expected)} artifacts; all loaders have standard and MiSans editions')
