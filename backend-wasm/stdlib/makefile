.PHONY: build

build:
	clang-12 --target=wasm32 -emit-llvm -c -S Core.IntToString.c
	llc-12 -march=wasm32 -filetype=obj Core.IntToString.ll
