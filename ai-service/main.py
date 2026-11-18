from fastapi import FastAPI
from pydantic import BaseModel
from typing import Optional
import uvicorn
from google import genai
from dotenv import load_dotenv
import json
import logging
from datetime import datetime
import os

# Load environment variables
load_dotenv()


# Logging configuration
logging.basicConfig(level = logging.INFO)
logger = logging.getLogger(__name__)

api_key = os.getenv("GEMINI_API_KEY")
if not api_key:
    raise ValueError("GEMINI_API_KEY not found, Check your .env file.")
client = genai.Client(api_key = api_key)

app = FastAPI(
    title="AI Fraud Detection Service — Powered by Gemini",
    description="""
    Real AI fraud detection using Gemini (Google).
    
    Gemini analyzes each trade and returns:
    - Fraud score (0.0 to 1.0)
    - Whether order is suspicious
    - Detailed reasoning
    
    This is REAL AI — not hardcoded rules.
    """,
    version="2.0.0"
)

class OrderData(BaseModel):
    orderId: str
    userId: str
    symbol: str
    orderType: str
    quantity: int
    totalAmount: float
    price: float
    recentOrderCount: Optional[int] = 0

class FraudCheckResult(BaseModel):
    orderId: str
    isSuspicious: bool
    fraudScore: float
    reason: Optional[str]
    recommendation: str
    aiProvider: str = "Gemini (Google)"
    checkedAt: str

@app.get("/health")
def health():
    return {
        "status": "UP",
        "service": "ai-fraud-detection",
        "aiProvider": "Gemini (Google)"
    }

@app.post("/api/fraud/check", response_model = FraudCheckResult)
def check_fraud(order: OrderData):
    """
    Real AI Fraud Detection using Gemini
    Send order details to Gemini API, Gemini will analyzes and return assessment
    No hardcoded rules - actual AI intelligence
    """
    logger.info("Gemini fraud check - orderId: {order.orderId} "
                f"symbol: {order.symbol} "
                f"type: {order.orderType} "
                f"quantity: {order.quantity} "
                f"totalAmount: {order.totalAmount} ")
    try:
        # Build prompt for Gemini
        prompt = f"""You are an AI fraud detection system for a Stock Trading Platform.
        
        Analyze the following trade order and determine if it is suspicious:
        Order Details:
        - Order ID: {order.orderId}
        - User ID: {order.userId}
        - Stock Symbol: {order.symbol}
        - Order Type: {order.orderType}
        - Quantity: {order.quantity} shares
        - Price per share: ₹{order.price}
        - Total Amount: ₹{order.totalAmount}
        - Recent orders in last hour: {order.recentOrderCount} 

        Analyze this trade for:
        1. High frequency trading (too many orders in short time)
        2. Unusally large amount of Orders
        3. Suspicious quantity patterns
        4. Any other red flags

        Respond only with a valid JSON object in this exact format:
        {{
            "isSuspicious": true or false,
            "fraudScore": number between 0.0 and 1.0,
            "reason": "brief explanation if suspicious, null if clean",
            "recommendation": "BLOCK or ALLOW or MONITOR"
        }}

        Be reasonable - legitimate large trades by genuine investors should not be flagged.
        Only flag genuinely suspicious patterns."""

        # Call the Gemini API
        response = client.models.generate_content(
                                                model="gemini-2.5-flash",
                                                contents=prompt,
                                                config={"response_mime_type": "application/json"}
                                                )
        
        #Parse Gemini's response
        response_text = message.text.strip()
        logger.info(f"Gemini response: {response_text}")

        # Parse JSON response
        ai_result = json.loads(response_text)
        is_suspicious = ai_result.get("isSuspicious", False)
        fraud_score = float(ai_result.get("fraudScore", 0.0))
        reason = ai_result.get("reason", None)
        recommendation = ai_result.get("recommendation", "ALLOW")

        if is_suspicious: logger.warning(
            f"ORDER FLAGGED by Gemini - "
            f"orderId: {order.orderId} "
            f"score: {fraud_score} "
            f"reason: {reason} "
        )
        else: logger.info(
            f"Order cleared by Gemini - "
            f"orderId: {order.orderId} "
            f"score: {fraud_score}"
        )

        return FraudCheckResult(
            orderId = order.orderId,
            isSuspicious = is_suspicious,
            fraudScore = round(fraud_score, 2),
            reason = reason,
            recommendation = recommendation,
            aiProvider = "Gemini (Google)",
            checkedAt = datetime.now().isoformat()
        )

    except json.JSONDecodeError as e: 
        logger.error(f"Failed to parse Gemini response: {e}")
        # Fallback to allow if parsing fails
        return FraudCheckResult(
            orderId = order.orderId,
            isSuspicious = False,
            fraudScore = 0.0,
            reason = "AI parsing error - defaulting to allow",
            recommendation = "ALLOW",
            aiProvider = "Gemini (Google) - fallback",
            checkedAt = datetime.now().isoformat()
        )

    except Exception as e:
        logger.error(f"Gemini API error: {e}") 
        # Fallback - don't block orders if AI is down
        return FraudCheckResult(
                    orderId = order.orderId,
                    isSuspicious = False,
                    fraudScore = 0.0,
                    reason = "AI parsing error - defaulting to allow",
                    recommendation = "ALLOW",
                    aiProvider = "Gemini (Google) - fallback",
                    checkedAt = datetime.now().isoformat()
        )

if __name__ == "__main__":
    uvicorn.run(
                "main:app", 
                host = "0.0.0.0",
                port = 8086,
                reload = True
                )