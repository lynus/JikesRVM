function [ coef ] = gini_coef( data, draw)
sorted = sort(data);
total = sum(sorted);
len =length(sorted);
X = 1:len;
equal_Y = X/len;
Y = sorted/total;
total = 0;
for i = 1:len
    Y(i) = Y(i) + total;
    total = Y(i);
end
gap = sum(equal_Y - Y);
coef = gap * 2 / len;

end

