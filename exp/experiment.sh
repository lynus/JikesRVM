#!/bin/bash
dryrun=0
nursery=0
pack=0
conf_name=default_conf
let nurserySize=4*1024*1024  #default 4MB
showhelp()
{
 echo $0 -n -d -h -c conf_name -s nurserySize -p; 
 exit 1;
}
while getopts ":ndhc:s:p" arg
do
	case $arg in
		n)
			nursery=1
			;;
		d)
			dryrun=1
			;;
		h)
			showhelp
			;;
		c)
			conf_name=$OPTARG
			;;
		s)
			size_=$(echo $OPTARG|sed -e 's/[a-zA-Z]//')
			let nurserySize=$siz*1024*1024
			;;
		p)
			pack=1
			;;
		*)
			echo can not recognize option -$OPTARG
			showhelp
			;;
	esac
done
if [ $pack == '1' ]; then
	cd $conf_name
	find -name 'scratch'|xargs rm -rf
	find -name '*.jar'|xargs rm -rf
	cd ..
	echo 'packing '$conf_name'.tar.gz ...'
	tar czf $conf_name'.tar.gz' $conf_name/
	exit 0;
fi
	
rm faillist 2>/dev/null
rm failcmd 2>/dev/null
rootpath=`pwd`
rvmpath=$(dirname $rootpath)
#gc=('ms' 'mc' 'immx')
#gc=('genms' 'genimmx')
gc=('gencopy' 'semispace')
heapsize=('3000')
#gcpath=('MarkCompact' 'MarkSweep' 'Immix')
#gcpath=('GenMS' 'GenImmix')
gcpath=('GenCopy' 'SemiSpace')
bench=('eclipse' 'hsqldb' 'antlr' 'bloat' 'chart' 'fop' 'jython' 'luindex' 'lusearch' 'pmd' 'xalan')
#bench=( 'hsqldb' 'jython' 'fop' 'luindex' 'lusearch' 'pmd' 'eclipse')
#bench=('antlr' 'hsqldb' 'fop' 'jython')
for ((i=0;i<${#gc[@]};i++)); do
	for((j=0;j<${#heapsize[@]};j++)); do
        (
	for((k=0;k<${#bench[@]};k++)); do
		
		vm=$rvmpath'/dist/BaseBase'${gcpath[$i]}'_x86_64_m64-linux/rvm'
		combine=${gc[$i]}'_'${heapsize[$j]}'_'${bench[$k]}
		#heap='-Xms'${heapsize[$j]}'m -Xmx'${heapsize[$j]}'m'
		heap=' -Xmx'${heapsize[$j]}'m'
		logfileflag='-X:logFile='${combine}
		syslogflag='-X:sysLogfile=syslog_'${combine}
		prog_output='output_'${combine}
		flag='-X:gc:forceMutatorCountWrite=true -X:gc:threads=4 -X:gc:verbose=3 -jar dacapo-2006-10-MR2.jar'
		nurseryflag=' '
		if [ $nursery == 1 ]; then
			nurseryflag='-X:gc:countfornursery=true'
		fi
		nurseryflag=$nurseryflag' -X:gc:fixedNursery='$nurserySize
		cmd=${vm}' '${heap}' '${logfileflag}' '${syslogflag}' '$nurseryflag' '${flag}' '${bench[$k]}
		if [ $dryrun == 1 ]; then
			echo $cmd
			continue
		fi

		dir=${rootpath}'/'$conf_name'/dir_'${combine}
		mkdir -p $dir 2>/dev/null
		cd ${dir}
		if [ ! -f dacapo-2006-10-MR2.jar ]; then
			cp ~/.buildit_components_cache/dacapo-2006-10-MR2.jar .
		fi
		echo ${cmd} >cmd
		echo 'start '${combine}
		if [ ${bench[$k]} == 'chart' ]; then
			xvfb-run -n ${i}${k}1 ${cmd} >${prog_output} 2>&1
		else
			${cmd} >${prog_output} 2>&1
		fi
		ret=$?
		if [ $ret -ne -0 ]; then
			echo ${combine}' failed!!!'
			echo ${combine} >>${rootpath}'/faillist_'$conf_name
			echo $cmd >>${rootpath}/failcmd_$conf_name
		else
			echo ${combine}' success.'
		fi
			
done
)&
done
done
wait
