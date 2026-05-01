######################################################################
#!/bin/bash
######################################################################

ANSI_RED="\033[31;1m"
ANSI_GREEN="\033[32;1m"
ANSI_RESET="\033[0m"
ANSI_CLEAR="\033[0K"

print_step () {
    echo -e "\n${ANSI_GREEN}$1${ANSI_RESET}"
}

# Define patterns and systems arrays
patterns=("compute-sync" "compute-app" "compute-cross" "unbound-collection" "unbound-allocation" "unbound-os")
systems=("CA-3.0.10" "CA-3.0.17" "CA-3.0.18" "CA-3.11.0" "CA-4.0.0" "CA-4.1.0" "HD-3.1.0" "HD-3.4.0")

# Loop through each pattern and system combination and run the analysis.py command
for pattern in "${patterns[@]}"
do
  for system in "${systems[@]}"
  do
    print_step "Running analysis for $pattern on $system"
    python analysis.py "$pattern" "$system"
  done
done