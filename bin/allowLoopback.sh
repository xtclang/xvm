for ((i=10; i<=99; i++)); do
    ifconfig lo0 alias 127.0.0.$i
done