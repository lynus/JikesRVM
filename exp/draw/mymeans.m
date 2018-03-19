function means = mymeans(vec, len)
means=[];
n=length(vec);
i=1;
while(i + len <= n)
    m = floor(mean(vec(i:i+len-1)));
    means=[means m];
    i=i+len;
end