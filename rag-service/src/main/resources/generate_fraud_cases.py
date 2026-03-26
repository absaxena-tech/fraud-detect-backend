import csv
import random
from datetime import datetime, timedelta

fraud_types = [
    "PHISHING", "SIM_SWAP", "CARD_TESTING", "ACCOUNT_TAKEOVER",
    "CRYPTO_PURCHASE", "GAMBLING", "MONEY_MULE", "WIRE_TRANSFER",
    "ATM_WITHDRAWAL", "FAKE_REFUND", "CHARGEBACK_FRAUD",
    "E_COMMERCE_FRAUD", "UPI_FRAUD", "MERCHANT_COLLUSION"
]

channels = ["CARD", "UPI", "NETBANKING", "WALLET", "ATM"]
risk_levels = ["LOW", "MEDIUM", "HIGH", "CRITICAL"]

merchant_categories = [
    "CRYPTO_EXCHANGE", "GAMBLING_SITE", "MONEY_TRANSFER", "E_COMMERCE",
    "ATM", "RETAIL", "FOOD", "TRAVEL", "ELECTRONICS", "GIFT_CARD"
]

locations = ["India", "USA", "UK", "Singapore", "UAE", "Germany", "Canada", "Australia"]

def random_amount(fraud_type):
    if fraud_type in ["CARD_TESTING"]:
        return round(random.uniform(1, 20), 2)
    if fraud_type in ["ATM_WITHDRAWAL"]:
        return round(random.uniform(200, 2000), 2)
    if fraud_type in ["WIRE_TRANSFER", "MONEY_MULE"]:
        return round(random.uniform(5000, 50000), 2)
    return round(random.uniform(100, 15000), 2)

def build_description(fraud_type, channel, amount, location, category):
    base = f"Transaction of {amount} USD via {channel} in {location} under category {category}. "

    patterns = {
        "PHISHING": "User credentials were compromised through phishing. New device login detected followed by high-value transfer.",
        "SIM_SWAP": "SIM swap suspected. OTP delivered to new SIM and unauthorized transfer initiated.",
        "CARD_TESTING": "Multiple low-value test transactions across merchants followed by a sudden high purchase attempt.",
        "ACCOUNT_TAKEOVER": "Account takeover suspected due to device mismatch and unusual transaction behavior.",
        "CRYPTO_PURCHASE": "Rapid crypto purchases from unfamiliar IP address, inconsistent with customer history.",
        "GAMBLING": "Unusual gambling site payments with no prior gambling activity on the account.",
        "MONEY_MULE": "Funds transferred to mule accounts with multiple beneficiaries in short time window.",
        "WIRE_TRANSFER": "High-value overseas wire transfer initiated from new device and new IP.",
        "ATM_WITHDRAWAL": "Multiple ATM withdrawals within minutes across different locations.",
        "FAKE_REFUND": "Refund requested for high-value purchase without shipment confirmation. Possible refund fraud.",
        "CHARGEBACK_FRAUD": "Customer raised chargeback after successful delivery. Pattern matches chargeback fraud.",
        "E_COMMERCE_FRAUD": "High-value e-commerce purchase using mismatched billing/shipping details.",
        "UPI_FRAUD": "Unauthorized UPI transfer initiated after social engineering call.",
        "MERCHANT_COLLUSION": "Repeated transactions to same merchant with suspicious split payments pattern."
    }

    return base + patterns.get(fraud_type, "Suspicious activity detected requiring manual review.")

def generate_csv(filename="fraud_cases.csv", rows=10000):
    start_time = datetime.now() - timedelta(days=365)

    with open(filename, mode="w", newline="", encoding="utf-8") as file:
        writer = csv.writer(file)

        # Header
        writer.writerow([
            "caseId", "fraudType", "channel", "riskLevel",
            "amount", "currency", "merchantCategory",
            "location", "timestamp", "description"
        ])

        for i in range(rows):
            case_id = f"CASE-{100000+i}"
            fraud_type = random.choice(fraud_types)
            channel = random.choice(channels)
            risk = random.choice(risk_levels)
            category = random.choice(merchant_categories)
            location = random.choice(locations)

            amount = random_amount(fraud_type)
            currency = "USD"

            timestamp = start_time + timedelta(minutes=random.randint(0, 525600))
            timestamp_str = timestamp.strftime("%Y-%m-%d %H:%M:%S")

            description = build_description(fraud_type, channel, amount, location, category)

            writer.writerow([
                case_id, fraud_type, channel, risk,
                amount, currency, category,
                location, timestamp_str, description
            ])

    print(f"Generated {rows} fraud cases into {filename}")

if __name__ == "__main__":
    generate_csv("fraud_cases.csv", 10)