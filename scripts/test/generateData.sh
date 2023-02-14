#!/bin/bash

set -e

if [ $# -ne 2 ]; then
  echo 'please input 2 params'
  exit
fi

dstDir='./dataset/databases'
databaseNum=$1
dataSize=$2

if [ -d $dstDir ];
then
echo "delete existing dir"
rm -rf $dstDir
fi

mkdir $dstDir

i=0
while ((i < databaseNum))
do
    mkdir $dstDir/database$i
    ((i = i + 1))
done

((totalSize = databaseNum * dataSize))
echo "generating data, total size: $totalSize M"
cd ./dataset/TPC-H\ V3.0.1/dbgen/
./dbgen -f -s $totalSize
cd ../../..
pwd
i=0
while ((i < databaseNum))
do
	echo "separating data, running for database$i"
	((a = i * 150 + 1))
	((b = (i + 1) * 150))
	echo "C_CUSTKEY | C_NAME | C_ADDRESS | C_NATIONKEY | C_PHONE | C_ACCTBAL | C_MKTSEGMENT | C_COMMENT" > $dstDir/database$i/customer.csv
	sed -n "$a,$b"p ./dataset/TPC-H\ V3.0.1/dbgen/customer.tbl | sed 's/.$//' >> $dstDir/database$i/customer.csv

	((a = i * 6000 + 1))
	((b = (i + 1) * 6000))
	echo "L_ORDERKEY | L_PARTKEY | L_SUPPKEY | L_LINENUMBER | L_QUANTITY | L_EXTENDEDPRICE | L_DISCOUNT | L_TAX | L_RETURNFLAG | L_LINESTATUS | L_SHIPDATE | L_COMMITDATE | L_RECEIPTDATE | L_SHIPINSTRUCT | L_SHIPMODE | L_COMMENT" > $dstDir/database$i/lineitem.csv
	sed -n "$a,$b"p ./dataset/TPC-H\ V3.0.1/dbgen/lineitem.tbl | sed 's/.$//' >> $dstDir/database$i/lineitem.csv

	((a = i * 6000 + 1))
	((b = (i + 1) * 6000))
	echo "O_ORDERKEY | O_CUSTKEY | O_ORDERSTATUS | O_TOTALPRICE | O_ORDERDATE | O_ORDER-PRIORITY | O_CLERK | O_SHIP-PRIORITY | O_COMMENT" > $dstDir/database$i/orders.csv
	sed -n "$a,$b"p ./dataset/TPC-H\ V3.0.1/dbgen/orders.tbl | sed 's/.$//' >> $dstDir/database$i/orders.csv

	((a = i * 200 + 1))
	((b = (i + 1) * 200))
	echo "P_PARTKEY | P_NAME | P_MFGR | P_BRAND | P_TYPE | P_SIZE | P_CONTAINER | P_RETAILPRICE | P_COMMENT" > $dstDir/database$i/part.csv
	sed -n "$a,$b"p ./dataset/TPC-H\ V3.0.1/dbgen/part.tbl | sed 's/.$//' >> $dstDir/database$i/part.csv

	((a = i * 800 + 1))
	((b = (i + 1) * 800))
	echo "PS_PARTKEY | PS_SUPPKEY | PS_AVAILQTY | PS_SUPPLYCOST | PS_COMMENT" > $dstDir/database$i/partsupp.csv
	sed -n "$a,$b"p ./dataset/TPC-H\ V3.0.1/dbgen/partsupp.tbl | sed 's/.$//' >> $dstDir/database$i/partsupp.csv

	((a = i * 10 + 1))
	((b = (i + 1) * 10))
	echo "S_SUPPKEY | S_NAME | S_ADDRESS | S_NATIONKEY | S_PHONE | S_ACCTBAL | S_COMMENT" > $dstDir/database$i/supplier.csv
	sed -n "$a,$b"p ./dataset/TPC-H\ V3.0.1/dbgen/supplier.tbl | sed 's/.$//' >> $dstDir/database$i/supplier.csv

  	echo "N_NATIONKEY | N_NAME | N_REGIONKEY | N_COMMENT" > $dstDir/database$i/nation.csv
	sed 's/.$//' ./dataset/TPC-H\ V3.0.1/dbgen/nation.tbl >> $dstDir/database$i/nation.csv

	echo "R_REGIONKEY | R_NAME | R_COMMENT" > $dstDir/database$i/region.csv
	sed 's/.$//' ./dataset/TPC-H\ V3.0.1/dbgen/region.tbl >> $dstDir/database$i/region.csv
	((i = i + 1))
done
rm ./dataset/TPC-H\ V3.0.1/dbgen/*.tbl
