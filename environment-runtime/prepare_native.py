"""Prepare checksum-pinned Termux runtime binaries inside generated build output."""
import hashlib
import io
from pathlib import Path
import sys
import tarfile
import urllib.request

PACKAGES = (
    ("pool/main/p/proot/proot_5.1.107.102_aarch64.deb", "10a394b585c2fc2b2c8502544f4e5f1be581dc8bfff79e8ba62f16207423e030"),
    ("pool/main/libt/libtalloc/libtalloc_2.5.0_aarch64.deb", "556591f43bb773ad8777e1a29522640866a55f95dab71914418b94a8c58ad5a7"),
    ("pool/main/liba/libandroid-shmem/libandroid-shmem_0.7_aarch64.deb", "0da3a24d558b93c92bcf8d611e0826a99ff96e396b148e6cdf33b47c47c57ff6"),
)
NAMES = {"proot": "libproot.so", "loader": "libproot-loader.so", "libtalloc.so.2.5.0": "libtalloc.so", "libandroid-shmem.so": "libandroid-shmem.so"}


def prepare(output: Path):
    output.mkdir(parents=True, exist_ok=True)
    cache = Path(__file__).resolve().parent.parent / "temp/environment-runtime/packages"
    cache.mkdir(parents=True, exist_ok=True)
    for package, digest in PACKAGES:
        archive = cache / Path(package).name
        if not archive.exists() or hashlib.sha256(archive.read_bytes()).hexdigest() != digest:
            with urllib.request.urlopen("https://packages.termux.dev/apt/termux-main/" + package, timeout=60) as response:
                payload = response.read()
            if hashlib.sha256(payload).hexdigest() != digest:
                raise ValueError("Runtime package checksum mismatch")
            archive.write_bytes(payload)
        payload = archive.read_bytes()
        position = 8
        while position < len(payload):
            header = payload[position:position + 60]
            size = int(header[48:58])
            name = header[:16].decode().strip().rstrip("/")
            content = payload[position + 60:position + 60 + size]
            position += 60 + size + size % 2
            if not name.startswith("data.tar"):
                continue
            with tarfile.open(fileobj=io.BytesIO(content)) as tar:
                for entry in tar:
                    target = NAMES.get(Path(entry.name).name)
                    if not entry.isfile() or target is None:
                        continue
                    binary = tar.extractfile(entry).read()
                    # Android extracts lib*.so files, so use the matching dependency filename.
                    old = b"libtalloc.so.2\0"
                    binary = binary.replace(old, b"libtalloc.so\0".ljust(len(old), b"\0"))
                    # 使用 APK 解压目录中的相邻依赖，避免依赖 Termux 路径或被转译层忽略的环境变量。
                    old_runpath = b"/data/data/com.termux/files/usr/lib\0"
                    binary = binary.replace(old_runpath, b"$ORIGIN\0".ljust(len(old_runpath), b"\0"))
                    (output / target).write_bytes(binary)
    if any(not (output / name).exists() for name in NAMES.values()):
        raise ValueError("Incomplete native runtime")


if __name__ == "__main__":
    prepare(Path(sys.argv[1]))
