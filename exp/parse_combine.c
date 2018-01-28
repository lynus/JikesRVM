#include<stdio.h>
#include<string.h>
int main(int argc, char* argv[]) {
	char gc[16]="";
	char size[16]="";
	char bench[16]="";
	char gcpath[16]="";
	char *save;
	char *ptr = strchr(argv[1], '_');
	strncpy(gc,argv[1], ptr - argv[1]);
	ptr++;
	save = ptr;
	ptr = strchr(ptr, '_');
	strncpy(size, save, ptr-save);
	ptr++;
	strcpy(bench, ptr);
	if (strcmp(gc, "ms") ==0)
		strcpy(gcpath, "MarkSweep");
	else if (strcmp(gc, "mc")==0)
		strcpy(gcpath, "MarkCompact");
	else
		strcpy(gcpath, "Immix");
	printf("%s %s %s %s\n", gc, gcpath, size, bench);
	return 0;
}
