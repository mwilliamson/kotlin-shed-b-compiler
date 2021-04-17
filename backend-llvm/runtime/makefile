.PHONY: build/libshed.a build-deps build-bdwgc build-utf8proc

CFLAGS = -Wall -Werror

build/libshed.a: build-deps sizeof_jmp_buf.txt
	mkdir -p build
	cd build && cmake .. && make

build-deps: build-bdwgc build-utf8proc

build-bdwgc: deps/gc-8.0.4

deps/gc-8.0.4:
	#rm -rf deps/gc-8.0.4
	curl -L https://github.com/ivmai/bdwgc/releases/download/v8.0.4/gc-8.0.4.tar.gz | tar xzf - -C deps
	cd deps/gc-8.0.4 && ./configure --enable-static --enable-threads=no && make

build-utf8proc: deps/utf8proc-2.4.0

deps/utf8proc-2.4.0:
	#rm -rf deps/utf8proc-2.4.0
	curl -L https://github.com/JuliaStrings/utf8proc/archive/v2.4.0.tar.gz | tar xzf - -C deps
	cd deps/utf8proc-2.4.0 && make

sizeof_jmp_buf.txt: sizeof_jmp_buf.c
	gcc sizeof_jmp_buf.c -o sizeof_jmp_buf
	sizeof_jmp_buf > sizeof_jmp_buf.txt
