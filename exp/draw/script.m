clear;
clc;
rootpath=pwd;
mkdir('graphics');
figure('units','normalized','outerposition',[0 0 1 1])
list = dir('dir*');
for dirname = {list.name}
  dirname=dirname{1};
  combine=dirname(5:length(dirname));
  cd(dirname);
  counts=load(combine);
  counts=mymeans(counts, 256);
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
  set(gca,'XTick',[0:100:len]);
  index=find(combine == '_');
  combine(index(1))='-';
  combine(index(2))='-';
  title(combine);
  hold off;
  svgfile= [rootpath '/graphics/' combine '.jpg'];
  saveas(gcf, svgfile);
  cd('..');
end 