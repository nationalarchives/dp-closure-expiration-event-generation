# Closure expiration event generation

This lambda finds any records which have a review date in the past but are still closed. There are two steps:
1. Call the API client to get any records with a review date in the past and are still closed.
2. Send a json object to an SQS queue with one message per entity. We may batch this later if this is unsustainable.


