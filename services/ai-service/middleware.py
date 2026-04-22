import uuid
import time
import logging
from fastapi import Request
from metrics import REQUEST_COUNT, REQUEST_LATENCY, REQUEST_ERRORS

logger = logging.getLogger(__name__)

async def add_request_context(request: Request, call_next):
    request_id = str(uuid.uuid4())
    request.state.request_id = request_id

    start_time = time.time()
    endpoint = request.url.path 
    REQUEST_COUNT.labels(endpoint=endpoint, status="success").inc() #only goes up until restart - tracks number of request made 

    try:
        response = await call_next(request)
        REQUEST_COUNT.labels(endpoint=endpoint, status="success").inc() #success metrics
        response.headers["X-Request-ID"] = request_id #attaches the request id to response headers 
        return response

    except Exception:
        REQUEST_ERRORS.labels(endpoint=endpoint).inc() #counter for number of errors per api call
        REQUEST_COUNT.labels(endpoint=endpoint, status="error").inc()
        logger.error("Unhandled request error", extra={"request_id": request_id}, exc_info=True)
        raise

    finally:
        duration = time.time() - start_time 
        REQUEST_LATENCY.labels(endpoint=endpoint).observe(duration) #gives latency between requests