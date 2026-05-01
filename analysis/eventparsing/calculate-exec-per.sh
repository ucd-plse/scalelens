##########################################################################
#!/bin/bash
##########################################################################
##
source ../environment.sh

PLOTS="$1"
SIZE="$2"

## check some stuff firts
if [ ! "$#" -eq 2 ]; then
  echo "[$0] USAGE: $0 <plot_folder> <target_size>"
  echo "[$0] <plot_folder>: Folder containing the resulting plot data. Use absolute path."
  echo "[$0] <target_size>: The size of the cluster to get an approximate idea of percentages."
  exit 1
fi

HERE="$PWD"
DATA_NAME="growth.dat"
MAIN_TEX="main.tex"
## now, for each filder in our plot folder, do the hustle
for METHOD in $(ls "$PLOTS" | sort -n); do 
  ## ommit the eps folder
  if [ ! -f  $PLOTS/$METHOD/$DATA_NAME ]; then
    cd $HERE
    continue
  fi
  ## go there
  cd "$PLOTS/$METHOD"
  ## form the title
  T1=$(echo $METHOD | rev | cut -d'.' -f1 | rev)
  T2=$(echo $METHOD | rev | cut -d'.' -f2 | rev)
  T3=$(echo $METHOD | rev | cut -d'.' -f3 | rev)
  ORDER=$(echo $METHOD | cut -d'.' -f1)
  ## calculate number of points
  POINTS=$(cat $DATA_NAME | wc -l)
  PER=$(echo "scale=2; ($POINTS/$SIZE) * 100" | bc | cut -f1 -d'.')
  # LINE="$T3.$T2,$T1,$POINTS,$PER"
  LINE="$PER"
  echo $LINE
  ## come back
  cd $HERE
done
exit 0
