find main/ -type f ! -name "*.D" ! -name "*.json" -exec sh -c 'echo "=== {} ==="; cat "{}"; echo' \; > merged.txt

# todo : Implement trade Monitor scheduler
# todo : Check Pivot levels before the entry.
# Implement trailing SL and Target
# Similarly check the target with pivot levels.
# todo : OHL scanner

