import unittest

from verify_lifecycle_shim import normalized_code


COMPILED = """  public void counter();
    descriptor: ()V
    Code:
         0: ldc           #9                  // String counter
         2: ifeq          12
         5: aload_0
         6: invokevirtual #17                 // Method tick:()V
         9: goto          13
        12: return
        13: return
      Exception table:
         from    to  target type
             0    12    13   any

}
"""

RELOCATED = """  public void counter();
    descriptor: ()V
    Code:
         0: ldc_w         #290                 // String counter
         3: ifeq          13
         6: aload_0
         7: invokevirtual #317                // Method tick:()V
        10: goto          14
        13: return
        14: return
      Exception table:
         from    to  target type
             0    13    14   any

}
"""


class LifecycleBytecodeVerifierTest(unittest.TestCase):

    def test_accepts_only_constant_pool_width_and_offset_changes(self):
        self.assertEqual(normalized_code(COMPILED), normalized_code(RELOCATED))

    def test_retains_branch_destination(self):
        altered = RELOCATED.replace("ifeq          13", "ifeq          14")
        self.assertNotEqual(normalized_code(COMPILED), normalized_code(altered))

    def test_retains_exception_range_and_destination(self):
        for altered in [
            RELOCATED.replace("0    13    14", "0    14    14"),
            RELOCATED.replace("0    13    14", "0    13    13"),
        ]:
            self.assertNotEqual(normalized_code(COMPILED), normalized_code(altered))

    def test_retains_constant_values_and_method_targets(self):
        for altered in [
            RELOCATED.replace("String counter", "String changed"),
            RELOCATED.replace("Method tick", "Method wrong"),
        ]:
            self.assertNotEqual(normalized_code(COMPILED), normalized_code(altered))

    def test_retains_constant_pool_like_text_inside_string_literals(self):
        original = COMPILED.replace("String counter", "String task #123")
        equivalent = RELOCATED.replace("String counter", "String task #123")
        altered = equivalent.replace("String task #123", "String task #999")
        self.assertEqual(normalized_code(original), normalized_code(equivalent))
        self.assertNotEqual(normalized_code(original), normalized_code(altered))

    def test_retains_switch_destinations(self):
        code = """    Code:
         0: iconst_1
         1: tableswitch { // 1 to 2
                       1: 24
                       2: 25
                 default: 25
            }
        24: return
        25: return

"""
        altered = code.replace("1: 24", "1: 25")
        self.assertNotEqual(normalized_code(code), normalized_code(altered))

    def test_rejects_unknown_instruction_destinations(self):
        with self.assertRaises(SystemExit):
            normalized_code(COMPILED.replace("ifeq          12", "ifeq          11"))


if __name__ == "__main__":
    unittest.main()
