cmake_minimum_required(VERSION 3.18.1)
project(inputbridge C)
add_library(inputbridge SHARED input_bridge.c)
target_compile_options(inputbridge PRIVATE -Wall -O2)
