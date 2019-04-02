#!/bin/bash

# Block until Aion kernel p2p and rpc ports are open, using
# exponential back off for wait intervals.  This is a temporary
# measure until Test Harness randomizes the ports used by
# the kernel under test.

MAX_SLEEP_SECS=5400 # 90 min
SLEEP_BASE_SEC=9
MAX_ITERS=10

P2P_PORT=30303
RPC_PORT=8545

total="0"
iter="1"
while (netcat -z localhost $P2P_PORT || netcat -z localhost $RPC_PORT) && [ $iter -le $MAX_ITERS ]
do
    zzz=$((2**($iter-1)*$SLEEP_BASE_SEC))
    echo "Waiting $zzz sec for port $P2P_PORT and $RPC_PORT to be open (exponential backoff iter $iter of $MAX_ITERS.  Waited so far: $total sec)"

    sleep $zzz
    total=$((total+$zzz))
    ((iter++))
done

if ! netcat -z localhost $P2P_PORT && ! netcat -z localhost $RPC_PORT; then
    echo "Kernel ports are available after waiting $total sec"
    exit 0
else
    echo "Giving up; kernel ports still not available after waiting $total sec"
    exit 1
fi
