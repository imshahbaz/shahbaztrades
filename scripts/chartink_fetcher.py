import requests
import json

url = "https://chartink.com/screener/process"

headers = {
    "authority": "chartink.com",
    "method": "POST",
    "path": "/screener/process",
    "scheme": "https",
    "accept": "*/*",
    "accept-encoding": "gzip, deflate, br, zstd",
    "accept-language": "en-US,en;q=0.9",
    "content-type": "application/json",
    "cookie": "ajs_anonymous_id=%22252af6f6-82fd-4cc2-b565-5904303f2f67%22; remember_web_59ba36addc2b2f9401580f014c7f58ea4e30989d=eyJpdiI6IndBdjF2eG85SFN6MkdNZ2I3NnhiN3c9PSIsInZhbHVlIjoiV3pJcWdMTkJhMW9lU0I5cHdQLzhXWmxFV2ZwSHNjOWcrRjcvNWVEOUxQQ0dqM3JBTFZTRmxjOHREZ2J5SE1KeE1HN3JLL2VRY2J2N0J1dkFXc3QyVTJPZm1td3JFYWROTWt4NjI1VnpzODYzREppaDlrTlhRRGZHZGFRN1Q4aFB6RHhqWDQvMTY2bndzODVWdHJkL0orNmhPUGlTVE5hbVE3QjlnK1RMVnAvUXdCY2VJdk1MOUF6ZVRRR2RwVFRQcnJWT0RNV0xtZ2pEb1JJY0lVVnEyNHpFTXdRdGJUSytPeUFycnAzcmVHST0iLCJtYWMiOiIxYWE3YzIxNzg2MzAwZmMxOTU4NGQ3NmQzYmExYWIyMjlkMjdkNDMyNWMyMWNiZTc3ZTAzNjAxODUwYzlmYzM3IiwidGFnIjoiIn0%3D; _ga=GA1.1.603812378.1759268509; __gads=ID=594168aae844d17f:T=1758501842:RT=1765812198:S=ALNI_MaoPORi6d28wXN3GOwKyyVFDoDylQ; __gpi=UID=000011998243c526:T=1758501842:RT=1765812198:S=ALNI_MajZz0jTBvPCLewB7-2rU3e17l7Ag; __eoi=ID=83feb4b06f210d21:T=1758501842:RT=1765812198:S=AA-AfjaftKFiNTrDwIP2QB1zZlnC; XSRF-TOKEN=eyJpdiI6IktwK3FxMUdHTHRBa2tKM052YVBaamc9PSIsInZhbHVlIjoiSVYxbktwS1NDd3kxcXA5VTNXSnl0MU5KK3B3R2lWZFY5bEpndWVyL21QdGRLaXNQZ1RUZVJnQ3I5VjAybWRZeUNEVi9lNnEyV1oyUURWdmFUKzJudmxLYjNBa2lYS2d4QVdQTERIYmRLdk1ZaDJxMytCQmVEOStjNWFqUFltUDUiLCJtYWMiOiIyMWRiMjc0MGI5NmNjY2Q3YzUwYTdjMDFkNDhhZDJhZTI0YjE5ZWNhNDM0ZTNhZmM2OGUxZTlhZjAyNTcyYWE4IiwidGFnIjoiIn0%3D; ci_session=eyJpdiI6IkdhQWQ3VzRDbGkzT2RUak1LbkNFOGc9PSIsInZhbHVlIjoidVJWNmtoa2lHV1ZrcWEyZm1McTdzTGFGek5WVjc1eWI0QUU0c0lqWEpUZGduTGtLa3BDKythbGs5d3RFdnZFUWI1Q2xnMUFMOTNqWHIvQXc5VE1UeitrTlAweVZheEZSVjhiM0xaRzlMZThPajIzNkt2akFqaWphSTNZUW9COWkiLCJtYWMiOiIzOWY0MmNiNTQwMGNiNDY1NTFhM2ZkOTZiOTM0NTBjMTRjMjJiZjg2N2U0NjFlM2RlODcxNDVlNWM5Yjk5ZjI0IiwidGFnIjoiIn0%3D; _ga_7P3KPC3ZPP=GS2.1.s1765812196$o7$g1$t1765812231$j25$l0$h0",
    "origin": "https://chartink.com",
    "priority": "u=1, i",
    "referer": "https://chartink.com/screener/rsi-divergence-47474841",
    "sec-fetch-dest": "empty",
    "sec-fetch-mode": "cors",
    "sec-fetch-site": "same-origin",
    "user-agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1",
    "x-requested-with": "XMLHttpRequest",
    "x-xsrf-token": "eyJpdiI6IktwK3FxMUdHTHRBa2tKM052YVBaamc9PSIsInZhbHVlIjoiSVYxbktwS1NDd3kxcXA5VTNXSnl0MU5KK3B3R2lWZFY5bEpndWVyL21QdGRLaXNQZ1RUZVJnQ3I5VjAybWRZeUNEVi9lNnEyV1oyUURWdmFUKzJudmxLYjNBa2lYS2d4QVdQTERIYmRLdk1ZaDJxMytCQmVEOStjNWFqUFltUDUiLCJtYWMiOiIyMWRiMjc0MGI5NmNjY2Q3YzUwYTdjMDFkNDhhZDJhZTI0YjE5ZWNhNDM0ZTNhZmM2OGUxZTlhZjAyNTcyYWE4IiwidGFnIjoiIn0="
}

# The scan clause extracted from the page content
scan_clause = "( {46553} ( daily low < 1 day ago low and daily rsi( 14 ) > 1 day ago rsi( 14 ) and daily low > daily ema( daily close , 200 ) ) )"

payload = {
    "scan_clause": scan_clause
}

try:
    response = requests.post(url, headers=headers, json=payload)
    print(f"Status Code: {response.status_code}")
    print("Response Headers:")
    print(response.headers)
    print("\nResponse Body:")
    # Pretty print if JSON
    try:
        data = response.json()
        print(json.dumps(data, indent=2))
        
        # Save to file
        with open("chartink_response.json", "w") as f:
            json.dump(data, f, indent=2)
    except:
        print(response.text)
except Exception as e:
    print(f"Error: {e}")
