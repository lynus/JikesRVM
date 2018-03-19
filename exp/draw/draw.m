function draw(bench)
  figure('units','normalized','outerposition',[0 0 1 1]);
  global memsize;
  global gc;
  n_memsize = length(memsize);
  n_gc = length(gc);
  for i = 1 : n_memsize
    for j = 1 : n_gc
      mem_ = memsize{i};
      gc_ = gc{j};
      
      combine = [gc_ '_' mem_ '_' bench];
      dir = ['dir_' combine];
      cd(dir);
      
      subplot(n_memsize, n_gc, n_gc * (i - 1) + j);
      counts=load(combine);
      top2perc = top(counts, 0.02);
      top10perc = top(counts, 0.1);
      gini = gini_coef(counts, 1);
      counts=mymeans(counts, 1);
      %gini = gini_coef(counts, 1);
      len=numel(counts);
      c0=zeros(1,len);
      c1=c0;
      c2=c0;
      c0(3:3:len)=counts(3:3:len);
      c1(1:3:len)=counts(1:3:len);
      c2(2:3:len)=counts(2:3:len);
      bar(c0,'r','EdgeColor','r');
      hold on;
      bar(c1,'g', 'EdgeColor','g');
      bar(c2,'b','EdgeColor','b');
      %%set(gca,'XTick',[0:5:len]);
      index=find(combine == '_');
      combine(index(1))='-';
      combine(index(2))='-';
      title(combine);
      
      t = sprintf('top 2%%-%-.2f, top 10%%-%-.2f, gini coef-%-.2f',top2perc, top10perc, gini);
      ylim=get(gca,'ylim');
      xlim=get(gca,'xlim');
      text(xlim(2),ylim(2),t,'HorizontalAlignment', 'right','VerticalAlignment','top','FontSize',10);    
      hold off;
      cd('..');
    end
  end
  svgfile = ['graphics/' bench '.svg'];
  saveas(gcf, svgfile);
end