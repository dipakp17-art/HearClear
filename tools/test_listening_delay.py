import importlib.util
from pathlib import Path
import math
import unittest

spec = importlib.util.spec_from_file_location("delay", Path(__file__).with_name("measure_listening_delay.py"))
delay = importlib.util.module_from_spec(spec)
spec.loader.exec_module(delay)


class DelayCheck(unittest.TestCase):
    def test_known_external_channel_delay(self):
        for rate in (16000, 48000):
            ref = [0.0] * (rate * 2)
            for start, duration in ((0.15, 0.04), (0.37, 0.09), (0.72, 0.03), (1.12, 0.07)):
                for i in range(round(duration * rate)):
                    ref[round(start * rate) + i] = math.sin(2 * math.pi * 1000 * i / rate)
            shift = round(rate * 0.137)
            returned = [0.0] * shift + [v * 0.4 for v in ref[:-shift]]
            result = delay.estimate(ref, returned, rate, 300)
            self.assertLessEqual(abs(result["delay_ms"] - 137), 1)

    def test_silence_rejected(self):
        with self.assertRaises(ValueError):
            delay.estimate([0.0] * 16000, [0.0] * 16000, 16000)


if __name__ == "__main__":
    unittest.main()
