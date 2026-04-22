#central place for metrics
from prometheus_client import Counter, Histogram

REQUEST_COUNT = Counter(
    "app_requests_total",
    "Total requests",
    ["endpoint", "status"] #allows request count to be defined w/ labels
)

REQUEST_ERRORS = Counter(
    "app_errors_total",
    "Total errors",
    ["endpoint"] #permits request-errors to be defined with labels
)

REQUEST_LATENCY = Histogram(
    "app_latency_seconds",
    "Request latency",
    ["endpoint"]  #allows for request-latency to be defined with labels
)