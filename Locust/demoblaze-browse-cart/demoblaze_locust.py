import json
import random
import uuid

from locust import HttpUser, task, between

# Product ids 1-9, observed from GET /entries on demoblaze.com
PRODUCT_IDS = [str(i) for i in range(1, 10)]


class DemoblazeUser(HttpUser):
    """Replays the demoblaze.com browse -> view product -> add to cart flow
    against the real api.demoblaze.com REST backend used by js/index.js,
    js/prod.js and js/cart.js."""

    host = "https://api.demoblaze.com"
    wait_time = between(1, 3)

    def on_start(self):
        # demoblaze.com sets a "user=<guid>" cookie client-side for anonymous carts
        self.session_cookie = f"user={uuid.uuid4()}"

    @task
    def browse_view_and_add_to_cart(self):
        # 1. Home page product list (GET /entries)
        self.client.get("/entries", name="/entries (list products)")

        # 2. View a product detail page (POST /view)
        product_id = random.choice(PRODUCT_IDS)
        self.client.post(
            "/view",
            data=json.dumps({"id": product_id}),
            headers={"Content-Type": "application/json"},
            name="/view (product detail)",
        )

        # 3. Add that product to the cart (POST /addtocart)
        # prod_id is a JSON integer (observed via live browser capture), not a string
        payload = {
            "id": str(uuid.uuid4()),
            "cookie": self.session_cookie,
            "prod_id": int(product_id),
            "flag": False,
        }
        self.client.post(
            "/addtocart",
            data=json.dumps(payload),
            headers={"Content-Type": "application/json"},
            name="/addtocart",
        )
