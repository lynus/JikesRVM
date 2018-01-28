#!/bin/bash
rm faillist 2>/dev/null
rm failcmd 2>/dev/null
rootpath=`pwd`
gc=('ms' 'mc' 'immx')
heapsize=('500' '1000' '2000')
#heapsize=('500')
gcpath=('MarkCompact' 'MarkSweep' 'Immix')
bench=('eclipse' 'hsqldb' 'antlr' 'bloat' 'chart' 'fop' 'jython' 'luindex' 'lusearch' 'pmd' 'xalan')
#bench=('eclipse')
for ((i=0;i<${#gc[@]};i++)); do
	for((j=0;j<${#heapsize[@]};j++)); do
        (
	for((k=0;k<${#bench[@]};k++)); do
		
		vm='/home/lynus/java_staff/JikesRVM/dist/BaseBase'${gcpath[$i]}'_x86_64_m64-linux/rvm'
		combine=${gc[$i]}'_'${heapsize[$j]}'_'${bench[$k]}
		heap='-Xms'${heapsize[$j]}'m -Xmx'${heapsize[$j]}'m'
		logfileflag='-X:logFile='${combine}
		syslogflag='-X:sysLogfile=syslog_'${combine}
		prog_output='output_'${combine}
		flag='-X:vm:forceMutatorCountWrite=true -X:gc:threads=1 -X:gc:verbose=2 -jar dacapo-2006-10-MR2.jar'
		cmd=${vm}' '${heap}' '${logfileflag}' '${syslogflag}' '${flag}' '${bench[$k]}
		if [ $# -gt 1 ]; then
			if [ $1 == '-d' ]; then
				echo $cmd
				continue
			fi
		fi

		dir=${rootpath}'/dir_'${combine}
		mkdir $dir 2>/dev/null
		cd ${dir}
		if [ ! -f dacapo-2006-10-MR2.jar ]; then
			cp ~/.buildit_components_cache/dacapo-2006-10-MR2.jar .
		fi
		echo ${cmd} >cmd
		echo 'start '${combine}
		if [ ${bench[$k]} == 'chart' ]; then
			xvfb-run -n ${i}${j} ${cmd} >${prog_output} 2>&1
		else
			${cmd} >${prog_output} 2>&1
		fi
		ret=$?
		if [ $ret -ne -0 ]; then
			echo ${combine}' failed.'
			echo ${combine} >>${rootpath}'/faillist'
			echo $cmd >>${rootpath}/failcmd
		else
			echo ${combine}' success.'
		fi
			
done
)&
done
done
wait
		
		
		

