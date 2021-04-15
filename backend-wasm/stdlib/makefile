.PHONY: build

build:
	clang --target=wasm32 -emit-llvm -c -S Core.IntToString.c
	llc -march=wasm32 -filetype=obj Core.IntToString.ll
