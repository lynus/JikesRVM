#!/bin/bash
rootpath=`pwd`
i=0
declare -a combines
for line in `cat faillist`
do
	combines=(${combines[*]} $line)
done
for line in ${combines[*]}
do
(
	combine=(`./parse_fail $line`)
	gc=${combine[0]}
	gcpath=${combine[1]}
	size=${combine[2]}
	bench=${combine[3]}

		
	vm='/home/lynus/java_staff/JikesRVM/dist/BaseBase'${gcpath}'_x86_64_m64-linux/rvm'
	combine=${gc}'_'${size}'_'${bench}
	heap='-Xms'$size'm -Xmx'$size'm'
	
	logfileflag='-X:logFile='${combine}
	syslogflag='-X:sysLogfile=syslog_'${combine}
	prog_output='output_'${combine}
	flag='-X:vm:forceMutatorCountWrite=true -X:gc:threads=1 -X:gc:verbose=3 -jar dacapo-2006-10-MR2.jar'
	cmd=${vm}' '${heap}' '${logfileflag}' '${syslogflag}' '${flag}' '${bench}
	if [ $# -gt 1 ]; then
		if [ $1 == '-d' ]; then
			echo $cmd
			continue
		fi
	fi

	dir='dir_'${combine}
	mkdir $dir 2>/dev/null
	cd ${dir}
	if [ ! -f dacapo-2006-10-MR2.jar ]; then
		cp ~/.buildit_components_cache/dacapo-2006-10-MR2.jar .
	fi
	echo 'start '${combine}
	echo ${cmd} >cmd
	${cmd} >${prog_output} 2>&1
	ret=$?
	if [ $ret -ne -0 ]; then
		echo ${combine}' failed again.'
		echo ${combine} >>${rootpath}'/2_faillist'
		echo $cmd >>${rootpath}/2_failcmd
	else
		echo ${combine}' success.'
	fi
)&
done
