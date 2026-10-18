@echo off
setlocal

stripe listen --forward-to http://localhost:8080/api/v1/payments/webhook/stripe

