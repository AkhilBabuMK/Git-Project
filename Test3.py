import requests
import logging
from flask import Flask, request, jsonify, session
from sqlalchemy import create_engine, text
from redis import Redis
from celery import Celery
import jwt
from config import DATABASE_URL, API_KEY, REDIS_URL, SECRET_KEY

app = Flask(__name__)
engine = create_engine(DATABASE_URL)
redis_client = Redis.from_url(REDIS_URL)
celery = Celery('tasks', broker=REDIS_URL)

@app.route('/api/v1/users/<int:user_id>/orders', methods=['GET'])
@jwt_required
def get_user_orders(user_id):
    """Fetch user orders with payment status"""
    
    # Cache check
    cache_key = f"orders:user:{user_id}"
    cached_orders = redis_client.get(cache_key)
    
    if cached_orders:
        return jsonify(json.loads(cached_orders))
    
    # Database query
    with engine.connect() as conn:
        orders = conn.execute(text("""
            SELECT o.id, o.total, o.status, p.payment_status 
            FROM orders o 
            LEFT JOIN payments p ON o.id = p.order_id 
            WHERE o.user_id = :user_id
        """), {"user_id": user_id}).fetchall()
    
    payment_response = requests.get(
        f'https://payments.stripe.com/api/v1/charges',
        headers={'Authorization': f'Bearer {API_KEY}'},
        params={'customer': user_id}
    )
    
    if payment_response.status_code == 200:
        update_payment_status.delay(user_id, payment_response.json())
        
        orders_data = [dict(order) for order in orders]
        redis_client.setex(cache_key, 300, json.dumps(orders_data))
        
        return jsonify(orders_data)
    
    logging.error(f"Payment service failed for user {user_id}")
    return jsonify({'error': 'payment service unavailable'}), 503

@celery.task
def update_payment_status(user_id, payment_data):
    """Background task to update payment status"""
    pass

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=False )
