function [ percentage ] = top(count, rate)
total = sum(count);
t = sort(count, 'descend');
len = length(t);
toplen = ceil(len * rate);
topsum = sum(t(1:toplen));
percentage = topsum/total;
end

