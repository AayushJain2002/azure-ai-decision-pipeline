import logging
import time
import uuid

from fastapi import Request

from metrics import REQUEST_COUNT, REQUEST_ERRORS, REQUEST_LATENCY

logger = logging.getLogger(__name__)


async def add_request_context(request: Request, call_next):
    request_id = str(uuid.uuid4())
    request.state.request_id = request_id

    endpoint = request.url.path

    if endpoint == "/metrics":
        return await call_next(request)

    start_time = time.time()

    try:
        response = await call_next(request)

        if response.status_code < 400:
            REQUEST_COUNT.labels(endpoint=endpoint, status="success").inc()
        else:
            REQUEST_COUNT.labels(endpoint=endpoint, status="error").inc()
            REQUEST_ERRORS.labels(endpoint=endpoint).inc()

        response.headers["X-Request-ID"] = request_id
        return response

    except Exception:
        REQUEST_COUNT.labels(endpoint=endpoint, status="error").inc()
        REQUEST_ERRORS.labels(endpoint=endpoint).inc()

        logger.error(
            "Unhandled request error",
            extra={"request_id": request_id, "endpoint": endpoint},
            exc_info=True,
        )
        raise

    finally:
        duration = time.time() - start_time
        REQUEST_LATENCY.labels(endpoint=endpoint).observe(duration)