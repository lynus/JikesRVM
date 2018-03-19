function [gc, mem, bench]=get_gc_mem_bench()
  gc={};
  mem={};
  bench={};
  list=dir('dir*');
  for dirname = {list.name}
    dirname = dirname{1};
    combine = strsplit(dirname(5:length(dirname)), '_');
    gc_ = combine{1};
    mem_ = combine{2};
    bench_ = combine{3};
    result = strcmp(gc, gc_);
    if sum(result) == 0
      gc = {gc{:}, gc_};
    end
    result = strcmp(bench, bench_);
    if sum(result)  == 0
      bench = {bench{:}, bench_};
    end
    result = strcmp(mem, mem_);
    if sum(result) == 0
      mem = {mem{:}, mem_};
    end
  end
end