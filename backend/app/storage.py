import os
from pathlib import Path

# S3-compatible object storage when configured; local directory otherwise (dev).
S3_BUCKET = os.environ.get("NIROG_S3_BUCKET", "")
S3_ENDPOINT = os.environ.get("NIROG_S3_ENDPOINT", "")
LOCAL_DIR = Path(os.environ.get("NIROG_UPLOAD_DIR", "uploads"))


def put(key: str, data: bytes) -> None:
    if S3_BUCKET:
        import boto3

        client = boto3.client("s3", endpoint_url=S3_ENDPOINT or None)
        client.put_object(Bucket=S3_BUCKET, Key=key, Body=data)
    else:
        path = LOCAL_DIR / key
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)


def get(key: str) -> bytes:
    if S3_BUCKET:
        import boto3

        client = boto3.client("s3", endpoint_url=S3_ENDPOINT or None)
        return client.get_object(Bucket=S3_BUCKET, Key=key)["Body"].read()
    return (LOCAL_DIR / key).read_bytes()
