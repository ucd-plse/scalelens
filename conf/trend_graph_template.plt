set term pos eps color solid font ",24"


set size 1.2,1.2
set datafile separator ","

set output "@FILE_NAME.eps"
set title "@TITLE" font ",28"
set key top left font ",18"
set xrange[@XSTART:@XEND]
set yrange[@YSTART:@YEND]
unset xtics
unset ytics
set xlabel "# @X_NAME"

plot "multiple.dat" u 1:2 with l t "@EQUATION" dt 2 lw 8  lc rgb "red", \
     "growth.dat" u 1:2 with lp t "Profiling" lw 2 pt 6 ps 3 lc rgb "blue"