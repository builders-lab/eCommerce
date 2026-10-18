import Body from "../components/Body";

interface MainPageProps {
    drawerOpen: boolean;
    toggleDrawer: (nextState?: boolean) => void;
}

function MainPage({ drawerOpen, toggleDrawer }: MainPageProps) {
    return <Body drawerOpen={drawerOpen} toggleDrawer={toggleDrawer} />;
}

export default MainPage;
