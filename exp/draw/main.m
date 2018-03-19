clear;
clc;
graphics_toolkit = 'gnuplot';
rootpath=pwd;
mkdir('graphics');
global memsize;
global gc;
[gc memsize bench] = get_gc_mem_bench();
for bench_ = bench
  bench_ = bench_{1};
  draw(bench_);
end