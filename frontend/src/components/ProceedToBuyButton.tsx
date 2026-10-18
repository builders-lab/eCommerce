import { Button } from "@mui/material";
import ShoppingBagIcon from "@mui/icons-material/ShoppingBag";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../global_component/auth-context";
import { redirectToLogin } from "../global_component/authUtils";

interface ProceedToBuyButtonProps {
    fullWidth?: boolean;
}

export default function ProceedToBuyButton({ fullWidth = true }: ProceedToBuyButtonProps) {
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();

  const handleClick = () => {
    if (!isAuthenticated) {
      redirectToLogin(navigate, "/checkout");
      return;
    }
    navigate("/checkout");
  };

  return (
    <Button
      variant="contained"
      color="secondary"
      startIcon={<ShoppingBagIcon />}
      onClick={handleClick}
      fullWidth={fullWidth}
    >
      Proceed to Buy
    </Button>
  );
}
