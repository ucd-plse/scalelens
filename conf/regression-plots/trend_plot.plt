set term pos eps color solid font ",24"


set size 1.2,1.2
set datafile separator ","

set output "linear.eps"
set title "Linear" font ",32"
set key top left font ",18"
set xrange[1:19]
set yrange[0:1]
unset xtics
unset ytics
set xlabel "Dimension"

plot "linear-reg.dat" u 1:2 with l t "y = D * 0.05556" dt 2 lw 6  lc rgb "red", \
     "linear.dat" u 1:2 with lp t "Linear (Normalized)" lw 1 pt 6 ps 1 lc rgb "blue"

set output "quadratic.eps"
set title "Quadratic" font ",32"
set key top left font ",18"
set xrange[1:19]
set yrange[0:1]
unset xtics
unset ytics
set xlabel "Dimension"

plot "quadratic-reg.dat" u 1:2 with l t "y = D * 0.05556  -0.14167" dt 2 lw 6  lc rgb "red", \
     "quadratic.dat" u 1:2 with lp t "Quadratic (Normalized)" lw 1 pt 6 ps 1 lc rgb "blue"


set output "cubic.eps"
set title "Cubic" font ",32"
set key top left font ",18"
set xrange[1:19]
set yrange[0:1]
unset xtics
unset ytics
set xlabel "Dimension"

plot "cubic-reg.dat" u 1:2 with l t "y = D * 0.05159  -0.18740" dt 2 lw 6  lc rgb "red", \
     "cubic.dat" u 1:2 with lp t "Cubic (Normalized)" lw 1 pt 6 ps 1 lc rgb "blue"

set output "quartic.eps"
set title "Quartic" font ",32"
set key top left font ",18"
set xrange[1:19]
set yrange[0:1]
unset xtics
unset ytics
set xlabel "Dimension"

plot "quartic-reg.dat" u 1:2 with l t "y = D * 0.04721  -0.19763" dt 2 lw 6  lc rgb "red", \
     "quartic.dat" u 1:2 with lp t "Quartic (Normalized)" lw 1 pt 6 ps 1 lc rgb "blue"

set output "quintic.eps"
set title "Quintic" font ",32"
set key top left font ",18"
set xrange[1:19]
set yrange[0:1]
unset xtics
unset ytics
set xlabel "Dimension"

plot "quintic-reg.dat" u 1:2 with l t "y = D * 0.04331  -0.19563" dt 2 lw 6  lc rgb "red", \
     "quintic.dat" u 1:2 with lp t "Quintic (Normalized)" lw 1 pt 6 ps 1 lc rgb "blue"