##########################################################################
#!/bin/bash
##########################################################################
##
source ../environment.sh

PLOTS="$1"
X_NAME="$2"
MAX_X="$3"
FIGS_PER_ROW="$4"

## check some stuff firts
if [ ! "$#" -eq 4 ]; then
  echo "[$0] USAGE: $0 <plot_folder> <dimension> <max_x> <figs_per_row>"
  echo "[$0] <plot_folder>: Folder containing the resulting plot data. Use absolute path."
  echo "[$0] <dimension>: Name of the dimension."
  echo "[$0] <max_x>: Maximum value on the x axis."
  echo "[$0] <figs_per_row>: The number of figures per row in the latex consolidated file."
  exit 1
fi

## find the plot template
if ! isFile "$AGENT_PLOT_TEMPLATE"; then
  echo "[$0] [$AGENT_PLOT_TEMPLATE] file does not exist. See readme for instructions."
  exit 1
fi

HERE="$PWD"
TREND_PLOT_NAME="trend_plot.plt"
EQUATION_FILE="eq"
CORRELATION_FILE="corr"
DETAILS_FILE="det"
IMAGES_FOLDER="eps"
MAIN_TEX="main.tex"
rm -rf "$PLOTS/$IMAGES_FOLDER"
mkdir "$PLOTS/$IMAGES_FOLDER"
## now, for each filder in our plot folder, do the hustle
for METHOD in $(ls "$PLOTS" | sort -n); do 
  ## ommit the eps folder
  if [ "$METHOD" = "$IMAGES_FOLDER" ] || [ "$METHOD" = "$MAIN_TEX" ]; then
    cd $HERE
    continue
  fi
  ## go there
  cd "$PLOTS/$METHOD"
  ## copy the template
  cp $HERE/$AGENT_PLOT_TEMPLATE $TREND_PLOT_NAME
  ## get the equation
  EQUATION=$(cat $EQUATION_FILE)
  ## and the correlation text
  CORR=$(cat $CORRELATION_FILE)
  ## the details
  DET=$(cat $DETAILS_FILE)
  ## form the title
  T1=$(echo $METHOD | rev | cut -d'.' -f1 | rev)
  T2=$(echo $METHOD | rev | cut -d'.' -f2 | rev)
  T3=$(echo $METHOD | rev | cut -d'.' -f3 | rev)
  ORDER=$(echo $METHOD | cut -d'.' -f1)
  TITLE="$T3.$T2 ($T1) ($CORR, $DET)"
  echo "[$0] Plotting $TITLE"
  FF="$ORDER"_"trend_regression"
  ## perform all replacements
  sed -i "s/@FILE_NAME/$FF/g" $TREND_PLOT_NAME
  sed -i "s/@TITLE/$TITLE/g" $TREND_PLOT_NAME
  sed -i "s/@XSTART/0/g" $TREND_PLOT_NAME
  sed -i "s/@XEND/$MAX_X/g" $TREND_PLOT_NAME
  sed -i "s/@YSTART/0/g" $TREND_PLOT_NAME
  sed -i "s/@YEND/1.1/g" $TREND_PLOT_NAME
  sed -i "s/@EQUATION/$EQUATION/g" $TREND_PLOT_NAME
  sed -i "s/@X_NAME/$X_NAME/g" $TREND_PLOT_NAME
  ## and plot
  gnuplot $TREND_PLOT_NAME
  ## move the result
  mv "$FF.eps" "../$IMAGES_FOLDER"
  ## come back
  cd $HERE
done
## also, generate the tex file we need with all the images
ALL_FIG="$PLOTS/$MAIN_TEX"
echo "[$0] Creating [$ALL_FIG]"
COUNT=0
PRINTED=1
TOTAL=0
## we need to replace some params on the template head first
HEAD_TEMPLATE="$AGENT/tex_fig_template_head.tex"
HEAD_TEMPLATE_CP="__tmp"
cp $HEAD_TEMPLATE $HEAD_TEMPLATE_CP
sed -i "s/@IMAGES_FOLDER/$IMAGES_FOLDER/g" $HEAD_TEMPLATE_CP
cat  $HEAD_TEMPLATE_CP > "$ALL_FIG"
rm $HEAD_TEMPLATE_CP
## now, for each filder in our plot folder, do the hustle
for IMG in $(ls "$PLOTS/$IMAGES_FOLDER" | sort -n); do  
  TOTAL=$(($TOTAL + 1))
  if [ "$COUNT" -eq 0 ]; then
    echo ""  >> "$ALL_FIG"
    echo "  \begin{figure}[h!]" >> "$ALL_FIG"
    echo "    \centerline {" >> "$ALL_FIG"
  fi
  echo "      \includegraphics[width=\fgw]{$IMAGES_FOLDER/$IMG}" >> "$ALL_FIG" 
  if [ "$PRINTED" -eq "$FIGS_PER_ROW" ]; then
    echo "    }" >> "$ALL_FIG"
    echo "  \end{figure}" >> "$ALL_FIG"
    COUNT=0
    PRINTED=1
    continue
  fi
  COUNT=$(($COUNT + 1))
  PRINTED=$(($PRINTED + 1))
done  
MM=$(($TOTAL%$FIGS_PER_ROW))
if [ "$MM" -ne 0 ]; then
    echo "    }" >> "$ALL_FIG"
    echo "  \end{figure}" >> "$ALL_FIG"
fi
cat "$AGENT/tex_fig_template_coda.tex" >> "$ALL_FIG"
## and copy the makefile
cp "$AGENT/tex_fig_makefile" "$PLOTS/Makefile"
## also, copy the figures
cp "$AGENT/linear.eps" "$PLOTS/$IMAGES_FOLDER"
cp "$AGENT/quadratic.eps" "$PLOTS/$IMAGES_FOLDER"
cp "$AGENT/cubic.eps" "$PLOTS/$IMAGES_FOLDER"
cp "$AGENT/quartic.eps" "$PLOTS/$IMAGES_FOLDER"
cp "$AGENT/quintic.eps" "$PLOTS/$IMAGES_FOLDER"
## exit correctly
exit 0
