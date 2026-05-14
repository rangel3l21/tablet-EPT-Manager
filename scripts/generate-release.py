#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Generate release metadata for Amarok APKs.

Creates:
- releases/Amarok-vX.X.X.apk (symlink/copy)
- releases/latest.json (metadata with download info)
- releases/sha256.txt (checksums)
"""

import os
import json
import subprocess
import hashlib
import sys
from pathlib import Path
from datetime import datetime

# Força encoding UTF-8 em Windows
if sys.platform == "win32":
    import io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')


def get_file_hash(filepath, algorithm="sha256"):
    """Calculate file hash."""
    hash_obj = hashlib.new(algorithm)
    with open(filepath, "rb") as f:
        for chunk in iter(lambda: f.read(4096), b""):
            hash_obj.update(chunk)
    return hash_obj.hexdigest()


def get_apk_info():
    """Find the latest built APK."""
    apk_dir = Path("app/build/outputs/apk/foss/debug")
    
    if not apk_dir.exists():
        raise FileNotFoundError(f"APK directory not found: {apk_dir}")
    
    apk_files = list(apk_dir.glob("*.apk"))
    if not apk_files:
        raise FileNotFoundError(f"No APK files found in {apk_dir}")
    
    # Get the latest APK (by modification time)
    apk_file = max(apk_files, key=lambda p: p.stat().st_mtime)
    return apk_file


def get_version_from_apk(apk_path):
    """Extract version from APK filename or metadata."""
    filename = apk_path.name
    # Expected format: Amarok-v0.10.1+fd95cb3-foss.apk
    # Extract: 0.10.1+fd95cb3
    if "-foss" in filename:
        version_part = filename.split("Amarok-v")[1].split("-foss")[0]
        return version_part
    return "0.10.0"


def get_git_commit():
    """Get current git commit hash."""
    try:
        commit = subprocess.check_output(
            ["git", "rev-parse", "--short", "HEAD"],
            stderr=subprocess.DEVNULL
        ).decode().strip()
        return commit
    except:
        return "unknown"


def get_git_tag():
    """Get latest git tag."""
    try:
        tag = subprocess.check_output(
            ["git", "describe", "--tags", "--abbrev=0"],
            stderr=subprocess.DEVNULL
        ).decode().strip()
        return tag
    except:
        return None


def create_releases_dir():
    """Ensure releases directory exists."""
    releases_dir = Path("releases")
    releases_dir.mkdir(exist_ok=True)
    return releases_dir


def generate_metadata(apk_path, releases_dir):
    """Generate latest.json metadata."""
    version = get_version_from_apk(apk_path)
    commit = get_git_commit()
    tag = get_git_tag()
    
    apk_name = apk_path.name
    file_size = apk_path.stat().st_size
    sha256_hash = get_file_hash(apk_path, "sha256")
    
    metadata = {
        "version": version,
        "versionCode": 125,
        "appName": "Amarok",
        "packageName": "deltazero.amarok.foss",
        "flavor": "foss",
        "buildType": "debug",
        "buildTime": datetime.now().isoformat(),
        "commit": commit,
        "commitTag": tag,
        "releaseFile": apk_name,
        "fileSize": file_size,
        "fileSizeHuman": format_bytes(file_size),
        "sha256": sha256_hash,
        "downloadUrl": f"https://github.com/deltazefiro/Amarok-Hider/releases/download/{tag or 'latest'}/{apk_name}",
        "changelog": "Debug build with automatic release metadata generation"
    }
    
    return metadata, sha256_hash


def format_bytes(size_bytes):
    """Format bytes to human readable format."""
    for unit in ["B", "KB", "MB", "GB"]:
        if size_bytes < 1024.0:
            return f"{size_bytes:.2f} {unit}"
        size_bytes /= 1024.0
    return f"{size_bytes:.2f} TB"


def copy_apk_to_releases(apk_path, releases_dir):
    """Copy APK to releases directory."""
    target_path = releases_dir / apk_path.name
    
    # Use shutil for cross-platform copying
    import shutil
    shutil.copy2(str(apk_path), str(target_path))
    
    print(f"✅ Copied APK: {target_path}")
    return target_path


def main():
    print("=" * 70)
    print("📦 Gerando Metadados de Release para Amarok")
    print("=" * 70)
    
    try:
        # 1. Find APK
        print("\n🔍 Procurando APK...")
        apk_path = get_apk_info()
        print(f"✅ APK encontrado: {apk_path.name}")
        
        # 2. Create releases directory
        print("\n📁 Criando diretório releases/...")
        releases_dir = create_releases_dir()
        print(f"✅ Diretório: {releases_dir.absolute()}")
        
        # 3. Copy APK
        print("\n📋 Copiando APK para releases/...")
        release_apk = copy_apk_to_releases(apk_path, releases_dir)
        
        # 4. Generate metadata
        print("\n📊 Gerando metadados...")
        metadata, sha256_hash = generate_metadata(apk_path, releases_dir)
        
        # 5. Write latest.json
        latest_json_path = releases_dir / "latest.json"
        with open(latest_json_path, "w") as f:
            json.dump(metadata, f, indent=2)
        print(f"✅ Criado: latest.json")
        print(f"   Versão: {metadata['version']}")
        print(f"   Commit: {metadata['commit']}")
        print(f"   Tamanho: {metadata['fileSizeHuman']}")
        
        # 6. Write sha256.txt
        sha256_txt_path = releases_dir / "sha256.txt"
        with open(sha256_txt_path, "w") as f:
            f.write(f"{sha256_hash}  {apk_path.name}\n")
        print(f"✅ Criado: sha256.txt")
        
        # 7. Print summary
        print("\n" + "=" * 70)
        print("✨ RESUMO DA ESTRUTURA DE RELEASE")
        print("=" * 70)
        print(f"\nreleases/")
        print(f"├── {apk_path.name}")
        print(f"├── latest.json")
        print(f"└── sha256.txt")
        
        print(f"\n📍 Localização: {releases_dir.absolute()}")
        print(f"\n💾 SHA256: {sha256_hash[:16]}...")
        print(f"\n✅ Release gerado com sucesso!")
        print("=" * 70)
        
    except Exception as e:
        print(f"\n❌ Erro ao gerar release: {e}")
        import traceback
        traceback.print_exc()
        exit(1)


if __name__ == "__main__":
    main()
