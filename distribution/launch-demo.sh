#!/bin/sh
# SPDX-License-Identifier: Apache-2.0
# Resolve resources and output from this distribution, even when launched elsewhere.
demo_home=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd) || exit 1
cd "$demo_home" || exit 1
exec "$demo_home/bin/Valthorne-examples" @DEMO_ID@ "$@"
