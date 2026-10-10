{ pkgs ? import <nixpkgs> {} }:

# Optional NixOS development environment. Uses the user's nixpkgs revision;
# it is not a locked/reproducible distribution of the Minecraft dependency set.
pkgs.mkShell {
  packages = [ pkgs.jdk17 pkgs.python3 pkgs.git pkgs.bash ];
  JAVA_HOME = "${pkgs.jdk17}";
  # Make the pinned upstream client natives find their system dependencies.
  # GPU drivers still come from the host's graphics configuration.
  LD_LIBRARY_PATH = (pkgs.lib.makeLibraryPath [
    pkgs.stdenv.cc.cc.lib
    pkgs.libglvnd
    pkgs.openal
    pkgs.alsa-lib
    pkgs.udev
    pkgs.xorg.libX11
    pkgs.xorg.libXcursor
    pkgs.xorg.libXrandr
    pkgs.xorg.libXinerama
    pkgs.xorg.libXi
    pkgs.xorg.libXxf86vm
  ]) + ":/run/opengl-driver/lib";
}
