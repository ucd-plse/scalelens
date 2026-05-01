#!/bin/bash

files=("w-add-node.sh" "w-add-node-tok.sh" "w-add-row.sh" "w-add-table-compact.sh" 
       "w-add-table-row.sh" "w-add-table-row-repair.sh" "w-add-table-snapshot.sh" "w-add-table.sh")

total_non_blank_lines=0
total_lines=0

for filename in "${files[@]}"; do
    if [ ! -f "$filename" ]; then
        echo "File $filename not found!"
        continue
    fi

    lineno=0
    non_blank_lines=0
    while IFS= read -r line; do
        lineno=$((lineno + 1))
        if [ -n "$line" ]; then
            non_blank_lines=$((non_blank_lines + 1))
            total_non_blank_lines=$((total_non_blank_lines + 1))
            total_lines=$((total_lines + lineno))
        fi
    done < "$filename"
done

echo "Total non-blank lines across all files: $total_non_blank_lines"
